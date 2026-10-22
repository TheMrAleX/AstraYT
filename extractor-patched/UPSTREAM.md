# NewPipe YouTube hotfix (GPL-3.0-or-later)

Base: TeamNewPipe/NewPipeExtractor v0.26.5. The two Java files are backported
from https://github.com/TeamNewPipe/NewPipeExtractor/commit/e8f89145a830b4d6cae32412007f24ea8b36c73a.
They backport PR #1545 and its follow-up: current YouTube n-parameter parsing.
Gradle recompiles these sources and replaces these two classes plus the Rhino adapter in the stable JAR.
All other extractor classes and dependencies remain from the stable release.
No precompiled patched binary is committed. Remove this module when a stable release
includes the fix; review compatibility before bumping the base.

Local correction: pass the function name (not its declaration plus semicolon) to
`buildHelperFunction`; otherwise the generated call is invalid JavaScript.
An offline regression test executes the generated helper with external dependencies.

JavaScript.java adapts the v0.26.5 Rhino wrapper to ES6 lexical scope (required by
`const` inside the new helper loop), rejects Java class access and observes thread
interruption during JS execution so coroutine deadlines can stop CPU work.

Use `var key` in the helper loop: Rhino 1.8.1 retains the first `const key`
across iterations even in ES6 mode. The regression fixture catches the unchanged
parameter result; merely compiling the helper does not detect it.
