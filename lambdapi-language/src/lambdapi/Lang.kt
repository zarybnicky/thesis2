package lambdapi

import com.oracle.truffle.api.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.nodes.*
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@TruffleLanguage.Registration(
        id = "lambdapi",
        name = "lambdapi",
        version = "0.1",
        defaultMimeType = "application/x-lambdapi",
        characterMimeTypes = ["application/x-lambdapi"],
        contextPolicy = TruffleLanguage.ContextPolicy.SHARED,
        fileTypeDetectors = [Lang.Detector::class]
)
class Lang : TruffleLanguage<Lang.Context>() {
    class Detector : TruffleFile.FileTypeDetector {
        override fun findEncoding(@Suppress("UNUSED_PARAMETER") file: TruffleFile): Charset = StandardCharsets.UTF_8
        override fun findMimeType(file: TruffleFile): String? {
            val name = file.name ?: return null
            if (name.endsWith(".lp")) return "application/x-lambdapi"
            try {
                file.newBufferedReader(StandardCharsets.UTF_8).use { fileContent ->
                    val firstLine = fileContent.readLine()
                    if (firstLine != null && firstLine.matches("^#!/usr/bin/env lambdapi".toRegex()))
                        return "application/x-lambdapi"
                }
            } catch (e: IOException) { // ok
            } catch (e: SecurityException) { // ok
            }
            return null
        }
    }

    class Context(var env: Env)

    override fun createContext(env: Env): Context = Context(env)
    override fun isObjectOfLanguage(obj: Any): Boolean = false
    override fun parse(req: ParsingRequest): CallTarget =
        Truffle.getRuntime().createCallTarget(InlineCode(currentLanguage(), null))

    class InlineCode(
        language: Lang,
        fd: FrameDescriptor?
    ) : RootNode(language, fd) {
        override fun execute(frame: VirtualFrame) = 42
    }

    abstract class Exp<T> {
        abstract fun apply(frame: VirtualFrame): T
    }

    object FortyTwo : Exp<Int>() {
        override fun apply(frame: VirtualFrame): Int = 42
    }

    data class Add(var lhs: Exp<Int>, var rhs: Exp<Int>) : Exp<Int>() {
        override fun apply(frame: VirtualFrame): Int =
                lhs.apply(frame) + rhs.apply(frame)
    }

    // Construction an example AST
    // ---------------------------
    //
    // function TestRootNode() {
    //    for (i = 100000; i > 0; i--) { SomeFun() }
    // }
    class TestRootNode(language: Lang) : RootNode(language) {
        private var fn = SomeFun(language)
        private var ct: DirectCallNode? = null
        override fun execute(frame: VirtualFrame): Int {
            if (ct == null) {
                val rt = Truffle.getRuntime()
                CompilerDirectives.transferToInterpreterAndInvalidate()
                ct = rt.createDirectCallNode(rt.createCallTarget(fn))
                adoptChildren()
            }
            var i = 100000
            var res = 0
            while (i > 0) {
                res += ct?.call() as Int
                i -= 1
            }
            return res
        }
    }

    // function SomeFun() {
    //   result = 0
    //   for (count = 10000; count > 0; count--) {
    //     result += ((42 + 42) + (42 + 42)) + ((42 + 42) + (42 + 42))
    //   }
    // }
    class SomeFun(language: Lang) : RootNode(language) {
        private var repeating = DummyLoop()
        private var loop: LoopNode = Truffle.getRuntime().createLoopNode(repeating)
        override fun execute(frame: VirtualFrame): Int {
            loop.execute(frame)
            return repeating.result
        }
    }

    // Here we use Truffle's special support for a looping construct
    class DummyLoop : Node(), RepeatingNode {
        // This will be constant folded
        val child = Add(
                Add(Add(FortyTwo, FortyTwo), Add(FortyTwo, FortyTwo)),
                Add(Add(FortyTwo, FortyTwo), Add(FortyTwo, FortyTwo)))

        private var count = 10000
        var result = 0
        override fun executeRepeating(frame: VirtualFrame): Boolean =
                if (count <= 0)
                    false
                else {
                    val res = this.child.apply(frame)
                    result += res
                    count -= 1
                    true
                }
    }

    companion object {
        fun currentLanguage(): Lang = getCurrentLanguage(Lang::class.java)
    }

    fun run() {
        val rootNode = TestRootNode(Lang())
        val target = Truffle.getRuntime().createCallTarget(rootNode)
        println(target.call())
    }
}
