package montuno.truffle

import com.oracle.truffle.api.*
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode
import montuno.common.checkTopLevel
import montuno.syntax.TopLevel
import montuno.syntax.parsePreSyntax
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@TruffleLanguage.Registration(
    id = MontunoTruffle.LANGUAGE_ID,
    name = "Montuno",
    version = "0.1",
    interactive = true,
    internal = false,
    defaultMimeType = MontunoTruffle.MIME_TYPE,
    characterMimeTypes = [MontunoTruffle.MIME_TYPE],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED,
    fileTypeDetectors = [MontunoDetector::class]
)
class MontunoTruffle : TruffleLanguage<MontunoTruffleContext>() {
    override fun createContext(env: Env): MontunoTruffleContext = MontunoTruffleContext(this, env)
    override fun initializeContext(context: MontunoTruffleContext) {}
    override fun isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean) = true
    override fun isObjectOfLanguage(obj: Any): Boolean = false
    override fun parse(request: ParsingRequest): CallTarget {
        CompilerAsserts.neverPartOfCompilation()
        val pre = parsePreSyntax(request.source)
        val root = ProgramRootNode(this, FrameDescriptor(), pre)
        return Truffle.getRuntime().createCallTarget(root)
    }

    companion object {
        val top: MontunoTruffleContext get() = getCurrentContext(MontunoTruffle::class.java)
        const val LANGUAGE_ID = "montuno"
        const val MIME_TYPE = "application/x-montuno"
    }
}

class MontunoDetector : TruffleFile.FileTypeDetector {
    override fun findEncoding(@Suppress("UNUSED_PARAMETER") file: TruffleFile): Charset = StandardCharsets.UTF_8
    override fun findMimeType(file: TruffleFile): String? {
        val name = file.name ?: return null
        if (name.endsWith(".mn")) return MontunoTruffle.MIME_TYPE
        try {
            file.newBufferedReader(StandardCharsets.UTF_8).use { fileContent ->
                if ((fileContent.readLine() ?: "").matches("^#!/usr/bin/env montuno".toRegex()))
                    return MontunoTruffle.MIME_TYPE
            }
        } catch (e: IOException) { // ok
        } catch (e: SecurityException) { // ok
        }
        return null
    }
}

@TypeSystemReference(Types::class)
class ProgramRootNode(l: MontunoTruffle, fd: FrameDescriptor, private val pre: List<TopLevel>) : RootNode(l, fd) {
    override fun isCloningAllowed() = true
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        var res: Any = VUnit
        for (n in pre) {
            val x = checkTopLevel(MontunoTruffle.top, n)
            if (x != null) res = x
        }
        return res
    }
}

@TypeSystemReference(Types::class)
class TermRootNode(l: MontunoTruffle, @field:Child var root: Term, frame: MaterializedFrame) : RootNode(l, frame.frameDescriptor) {
    override fun execute(frame: VirtualFrame): Any {
        return root.execute(frame)
    }
}
