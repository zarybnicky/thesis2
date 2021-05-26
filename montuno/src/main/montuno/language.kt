package montuno

import com.oracle.truffle.api.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode
import montuno.interpreter.MontunoContext
import montuno.interpreter.VUnit
import montuno.interpreter.checkTopLevel
import montuno.syntax.TopLevel
import montuno.syntax.parsePreSyntax
import montuno.truffle.PureCompiler
import montuno.truffle.TruffleCompiler
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
class MontunoTruffle : Montuno() {
    override fun getContext(): MontunoContext = MontunoPure.top
    override fun initializeContext(context: MontunoContext) {
        context.top.lang = this
        context.top.ctx = context
        context.compiler = TruffleCompiler(context)
    }
    companion object {
        val lang: TruffleLanguage<MontunoContext> get() = getCurrentLanguage(MontunoTruffle::class.java)
        val top: MontunoContext get() = getCurrentContext(MontunoTruffle::class.java)
        const val LANGUAGE_ID = "montuno"
        const val MIME_TYPE = "application/x-montuno"
    }
}

@TruffleLanguage.Registration(
    id = MontunoPure.LANGUAGE_ID,
    name = "MontunoPure",
    version = "0.1",
    interactive = true,
    internal = false,
    defaultMimeType = MontunoPure.MIME_TYPE,
    characterMimeTypes = [MontunoPure.MIME_TYPE],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED,
)
class MontunoPure : Montuno() {
    override fun getContext(): MontunoContext = top
    override fun initializeContext(context: MontunoContext) {
        context.top.lang = this
        context.top.ctx = context
        context.compiler = PureCompiler(context)
    }
    companion object {
        val top: MontunoContext get() = getCurrentContext(MontunoPure::class.java)
        const val LANGUAGE_ID = "montuno-pure"
        const val MIME_TYPE = "application/x-montuno-pure"
    }
}

abstract class Montuno : TruffleLanguage<MontunoContext>() {
    abstract fun getContext(): MontunoContext
    override fun createContext(env: Env): MontunoContext = MontunoContext(env)
    override fun isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean) = true
    override fun isObjectOfLanguage(obj: Any): Boolean = false
    override fun getScope(ctx: MontunoContext) = ctx.top
    override fun parse(request: ParsingRequest): CallTarget {
        CompilerAsserts.neverPartOfCompilation()
        val root = ProgramRootNode(this, getCurrentContext(this.javaClass), parsePreSyntax(request.source))
        return Truffle.getRuntime().createCallTarget(root)
    }
}

class ProgramRootNode(l: TruffleLanguage<*>, val ctx: MontunoContext, private val pre: List<TopLevel>) : RootNode(l) {
    override fun isCloningAllowed() = true
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        CompilerAsserts.neverPartOfCompilation()
        val results = mutableListOf<Any>()
        for (e in pre) {
            try {
                val x = checkTopLevel(ctx, e)
                if (x != null) results.add(x)
            } catch (e: RuntimeException) {
                println(e)
                throw e
            }
        }
        for (res in results.dropLast(1)) println(res)
        return if (results.isEmpty()) VUnit else results.last()
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