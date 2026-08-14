using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using Org.BouncyCastle.Crypto.Parameters;
using Org.BouncyCastle.Crypto.Signers;

namespace LangouAssistant.Core.Update;

public sealed record ReleaseManifest(
    [property: JsonPropertyName("platform")] string Platform,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("minimum_supported_version")] string MinimumSupportedVersion,
    [property: JsonPropertyName("mandatory")] bool Mandatory,
    [property: JsonPropertyName("url")] string Url,
    [property: JsonPropertyName("size")] long Size,
    [property: JsonPropertyName("sha256")] string Sha256,
    [property: JsonPropertyName("signature")] string Signature,
    [property: JsonPropertyName("published_at")] string PublishedAt);

public static class ReleaseSignatureVerifier
{
    public static bool Verify(ReleaseManifest manifest, string publicKeyBase64)
    {
        try
        {
            var publicKey = Convert.FromBase64String(publicKeyBase64);
            var signature = Convert.FromBase64String(manifest.Signature);
            if (publicKey.Length != Ed25519PublicKeyParameters.KeySize ||
                signature.Length != 64)
            {
                return false;
            }

            var payload = CanonicalPayload(manifest);
            var verifier = new Ed25519Signer();
            verifier.Init(false, new Ed25519PublicKeyParameters(publicKey));
            verifier.BlockUpdate(payload, 0, payload.Length);
            return verifier.VerifySignature(signature);
        }
        catch (Exception exception) when (
            exception is FormatException or ArgumentException or InvalidOperationException)
        {
            return false;
        }
    }

    internal static byte[] CanonicalPayload(ReleaseManifest manifest)
    {
        using var stream = new MemoryStream();
        using (var writer = new Utf8JsonWriter(stream))
        {
            writer.WriteStartObject();
            writer.WriteBoolean("mandatory", manifest.Mandatory);
            writer.WriteString("minimum_supported_version", manifest.MinimumSupportedVersion);
            writer.WriteString("platform", manifest.Platform);
            writer.WriteString("published_at", manifest.PublishedAt);
            writer.WriteString("sha256", manifest.Sha256);
            writer.WriteNumber("size", manifest.Size);
            writer.WriteString("url", manifest.Url);
            writer.WriteString("version", manifest.Version);
            writer.WriteEndObject();
        }
        return stream.ToArray();
    }
}

public enum ReleaseDecision
{
    Current,
    Optional,
    Mandatory,
    Rejected,
}

public static partial class ReleaseUpdatePolicy
{
    public static ReleaseDecision Evaluate(string currentVersion, ReleaseManifest manifest)
    {
        if (!string.Equals(manifest.Platform, "windows", StringComparison.Ordinal) ||
            !Uri.TryCreate(manifest.Url, UriKind.Absolute, out var url) ||
            !string.Equals(url.Scheme, Uri.UriSchemeHttps, StringComparison.Ordinal) ||
            !SemVersion.TryParse(currentVersion, out var current) ||
            !SemVersion.TryParse(manifest.Version, out var latest) ||
            !SemVersion.TryParse(manifest.MinimumSupportedVersion, out var minimum))
        {
            return ReleaseDecision.Rejected;
        }

        if (latest.CompareTo(current) <= 0)
        {
            return ReleaseDecision.Current;
        }

        return manifest.Mandatory || current.CompareTo(minimum) < 0
            ? ReleaseDecision.Mandatory
            : ReleaseDecision.Optional;
    }

    private sealed record SemVersion(int Major, int Minor, int Patch, string? Prerelease)
        : IComparable<SemVersion>
    {
        public int CompareTo(SemVersion? other)
        {
            if (other is null)
            {
                return 1;
            }

            var numeric = Major.CompareTo(other.Major);
            if (numeric == 0) numeric = Minor.CompareTo(other.Minor);
            if (numeric == 0) numeric = Patch.CompareTo(other.Patch);
            if (numeric != 0) return numeric;
            if (Prerelease is null && other.Prerelease is not null) return 1;
            if (Prerelease is not null && other.Prerelease is null) return -1;
            return string.Compare(Prerelease, other.Prerelease, StringComparison.Ordinal);
        }

        public static bool TryParse(string value, out SemVersion version)
        {
            var match = SemVersionRegex().Match(value);
            if (!match.Success ||
                !int.TryParse(match.Groups[1].Value, out var major) ||
                !int.TryParse(match.Groups[2].Value, out var minor) ||
                !int.TryParse(match.Groups[3].Value, out var patch))
            {
                version = new SemVersion(0, 0, 0, null);
                return false;
            }

            version = new SemVersion(
                major,
                minor,
                patch,
                match.Groups[4].Success ? match.Groups[4].Value : null);
            return true;
        }
    }

    [GeneratedRegex(
        @"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z.-]+))?$",
        RegexOptions.CultureInvariant)]
    private static partial Regex SemVersionRegex();
}
