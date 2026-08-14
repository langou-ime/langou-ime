# Langou Website

Private source and release controls for `langou.tech`.

The public site stays on the existing `website-v3` static baseline. Before the
v1.0.0 release, `live-snapshot/` is synchronized from production and used as
the hash-locked deployment source. The files in `static-patch/` may replace
only the Chinese and English privacy pages after their recorded source hashes
match.

The Next.js project is a local release-manifest reference implementation and
test harness. It is not a replacement for the current public website.
