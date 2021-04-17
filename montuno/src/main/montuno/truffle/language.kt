package montuno.truffle

import com.oracle.truffle.api.*
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.*
import montuno.syntax.parsePreSyntax
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets


@TruffleLanguage.Registration(
        id = MontunoLanguage.LANGUAGE_ID,
        name = "Montuno",
        version = "0.1",
        interactive = true,
        internal = false,
        defaultMimeType = MontunoLanguage.MIME_TYPE,
        characterMimeTypes = [MontunoLanguage.MIME_TYPE],
        contextPolicy = TruffleLanguage.ContextPolicy.SHARED,
        fileTypeDetectors = [MontunoDetector::class]
)
class MontunoLanguage : TruffleLanguage<MontunoContext>() {
    override fun createContext(env: Env): MontunoContext = MontunoContext(this)
    override fun isObjectOfLanguage(obj: Any): Boolean = false
    override fun parse(request: ParsingRequest): CallTarget {
        CompilerAsserts.neverPartOfCompilation()
        val pre = parsePreSyntax(request.source)
        val nodes = pre.map { toExecutableNode(it, this) }.toTypedArray()
        return ProgramRootNode(this, FrameDescriptor(), nodes, currentContext.globalFrame).target
    }

    companion object {
        val currentContext: MontunoContext get() = getCurrentContext(MontunoLanguage::class.java)
        const val LANGUAGE_ID = "montuno"
        const val MIME_TYPE = "application/x-montuno"
    }
}

class MontunoContext(val language: MontunoLanguage) {
    var globalFrame: MaterializedFrame
    init {
        val frameDescriptor = FrameDescriptor()
        val frame = Truffle.getRuntime().createVirtualFrame(null, frameDescriptor)
        //TODO: add builtins here
        // virtualFrame.setObject(frameDescriptor.addFrameSlot("*"), createBuiltinFunction(lang, MulBuiltinNodeFactory.getInstance(), virtualFrame));
        globalFrame = frame.materialize()
    }
}

class MontunoDetector : TruffleFile.FileTypeDetector {
    override fun findEncoding(@Suppress("UNUSED_PARAMETER") file: TruffleFile): Charset = StandardCharsets.UTF_8
    override fun findMimeType(file: TruffleFile): String? {
        val name = file.name ?: return null
        if (name.endsWith(".mn")) return MontunoLanguage.MIME_TYPE
        try {
            file.newBufferedReader(StandardCharsets.UTF_8).use { fileContent ->
                if ((fileContent.readLine() ?: "").matches("^#!/usr/bin/env montuno".toRegex()))
                    return MontunoLanguage.MIME_TYPE
            }
        } catch (e: IOException) { // ok
        } catch (e: SecurityException) { // ok
        }
        return null
    }
}

abstract class Exp<T> {
    abstract fun apply(frame: VirtualFrame): T
}

object FortyTwo : Exp<Int>() {
    override fun apply(frame: VirtualFrame): Int = 42
}

data class Add(var lhs: Exp<Int>, var rhs: Exp<Int>) : Exp<Int>() {
    override fun apply(frame: VirtualFrame): Int = lhs.apply(frame) + rhs.apply(frame)
}

class TestRootNode(language: MontunoLanguage) : RootNode(language) {
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

class SomeFun(language: MontunoLanguage) : RootNode(language) {
    private var repeating = DummyLoop()
    private var loop: LoopNode = Truffle.getRuntime().createLoopNode(repeating)
    override fun execute(frame: VirtualFrame): Int {
        loop.execute(frame)
        return repeating.result
    }
}

class DummyLoop : Node(), RepeatingNode {
    val child = Add(
        Add(Add(FortyTwo, FortyTwo), Add(FortyTwo, FortyTwo)),
        Add(Add(FortyTwo, FortyTwo), Add(FortyTwo, FortyTwo))
    )
    private var count = 10000
    var result = 0
    override fun executeRepeating(frame: VirtualFrame): Boolean = if (count <= 0) false else {
        val res = this.child.apply(frame)
        result += res
        count -= 1
        true
    }
}
