import httpx
import pytest

from langou_backend.sms import (
    AliyunSmsSender,
    SmsDeliveryError,
    _build_acs3_authorization,
)

OFFICIAL_SAMPLE_KEY_MATERIAL = "YourAccessKeySecret"
TEST_KEY_MATERIAL = "testAccessKeySecret"


def test_acs3_signature_matches_aliyun_official_fixed_example() -> None:
    authorization = _build_acs3_authorization(
        access_key_id="YourAccessKeyId",
        access_key_secret=OFFICIAL_SAMPLE_KEY_MATERIAL,
        method="POST",
        host="ecs.cn-shanghai.aliyuncs.com",
        action="RunInstances",
        version="2014-05-26",
        date="2023-10-26T10:22:32Z",
        nonce="3156853299f313e23d1673dc12e1703d",
        query={
            "ImageId": "win2019_1809_x64_dtc_zh-cn_40G_alibase_20230811.vhd",
            "RegionId": "cn-shanghai",
        },
        payload=b"",
    )

    assert authorization == (
        "ACS3-HMAC-SHA256 Credential=YourAccessKeyId,"
        "SignedHeaders=host;x-acs-action;x-acs-content-sha256;x-acs-date;"
        "x-acs-signature-nonce;x-acs-version,"
        "Signature=06563a9e1b43f5dfe96b81484da74bceab24a1d853912eee15083a6f0f3283c0"
    )


@pytest.mark.asyncio
async def test_aliyun_sender_uses_v3_signed_query_without_sdk() -> None:
    captured: httpx.Request | None = None

    def handle(request: httpx.Request) -> httpx.Response:
        nonlocal captured
        captured = request
        return httpx.Response(
            200,
            json={"Code": "OK", "Message": "OK", "RequestId": "request-id"},
        )

    async with httpx.AsyncClient(transport=httpx.MockTransport(handle)) as client:
        sender = AliyunSmsSender(
            access_key_id="testAccessKeyId",
            access_key_secret=TEST_KEY_MATERIAL,
            sign_name="懒狗输入法",
            template_code="SMS_123456789",
            client=client,
            date_factory=lambda: "2025-04-16T07:45:55Z",
            nonce_factory=lambda: "315484d3-b129-4966-974a-699b7ee56647",
        )

        await sender.send("+8613800138000", "123456")

    assert captured is not None
    assert captured.method == "POST"
    assert captured.url.host == "dysmsapi.aliyuncs.com"
    assert captured.content == b""
    assert dict(captured.url.params) == {
        "PhoneNumbers": "13800138000",
        "SignName": "懒狗输入法",
        "TemplateCode": "SMS_123456789",
        "TemplateParam": '{"code":"123456"}',
    }
    assert captured.headers["x-acs-action"] == "SendSms"
    assert captured.headers["x-acs-version"] == "2017-05-25"
    assert captured.headers["x-acs-content-sha256"] == (
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    )
    assert captured.headers["authorization"].startswith(
        "ACS3-HMAC-SHA256 Credential=testAccessKeyId,"
    )


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("status", "body"),
    [
        (200, {"Code": "isv.SMS_SIGNATURE_ILLEGAL", "Message": "invalid"}),
        (500, {"Code": "InternalError", "Message": "failed"}),
        (200, b"not-json"),
    ],
)
async def test_aliyun_sender_maps_all_remote_failures_to_safe_error(
    status: int,
    body: dict[str, str] | bytes,
) -> None:
    def handle(request: httpx.Request) -> httpx.Response:
        del request
        if isinstance(body, bytes):
            return httpx.Response(status, content=body)
        return httpx.Response(status, json=body)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handle)) as client:
        sender = AliyunSmsSender(
            access_key_id="testAccessKeyId",
            access_key_secret=TEST_KEY_MATERIAL,
            sign_name="懒狗输入法",
            template_code="SMS_123456789",
            client=client,
            date_factory=lambda: "2025-04-16T07:45:55Z",
            nonce_factory=lambda: "315484d3-b129-4966-974a-699b7ee56647",
        )

        with pytest.raises(SmsDeliveryError) as captured:
            await sender.send("+8613800138000", "123456")

    assert str(captured.value) == ""
    assert captured.value.__cause__ is None


@pytest.mark.asyncio
async def test_aliyun_sender_does_not_retry_transport_failure() -> None:
    calls = 0

    def handle(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        raise httpx.ReadTimeout("contains sensitive request URL", request=request)

    async with httpx.AsyncClient(transport=httpx.MockTransport(handle)) as client:
        sender = AliyunSmsSender(
            access_key_id="testAccessKeyId",
            access_key_secret=TEST_KEY_MATERIAL,
            sign_name="懒狗输入法",
            template_code="SMS_123456789",
            client=client,
        )

        with pytest.raises(SmsDeliveryError) as captured:
            await sender.send("+8613800138000", "123456")

    assert calls == 1
    assert captured.value.__cause__ is None
