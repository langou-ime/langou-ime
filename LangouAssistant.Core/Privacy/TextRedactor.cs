using System.Text.RegularExpressions;

namespace LangouAssistant.Core.Privacy;

public static partial class TextRedactor
{
    public static string Redact(string text)
    {
        if (string.IsNullOrEmpty(text))
        {
            return string.Empty;
        }

        var result = EmailRegex().Replace(text, "[邮箱]");
        result = ChineseIdentityRegex().Replace(result, "[身份证]");
        result = ChinesePhoneRegex().Replace(result, "[手机号]");
        result = BankCardRegex().Replace(result, "[银行卡]");
        return result;
    }

    [GeneratedRegex(@"(?<![\w.+-])[\w.+-]+@[\w-]+(?:\.[\w-]+)+(?![\w.-])", RegexOptions.CultureInvariant)]
    private static partial Regex EmailRegex();

    [GeneratedRegex(@"(?<!\d)\d{17}[\dXx](?!\d)", RegexOptions.CultureInvariant)]
    private static partial Regex ChineseIdentityRegex();

    [GeneratedRegex(@"(?<!\d)(?:\+?86[- ]?)?1[3-9]\d{9}(?!\d)", RegexOptions.CultureInvariant)]
    private static partial Regex ChinesePhoneRegex();

    [GeneratedRegex(@"(?<!\d)(?:\d[ -]?){16,19}(?!\d)", RegexOptions.CultureInvariant)]
    private static partial Regex BankCardRegex();
}
