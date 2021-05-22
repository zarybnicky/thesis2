package montuno.interpreter.scope

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import montuno.Lvl
import montuno.interpreter.MontunoContext

@ExportLibrary(InteropLibrary::class)
class TopScope(
    private val env: TruffleLanguage.Env
) : TruffleObject {
    lateinit var lang: TruffleLanguage<MontunoContext>
    lateinit var ctx: MontunoContext

    val it: MutableList<TopEntry> = mutableListOf()
    operator fun get(lvl: Lvl) = it[lvl.it]
    fun reset() {
        it.removeAll { true }
    }

    @ExportMessage
    fun hasMembers() = true
    @ExportMessage
    fun getMembers(includeInternal: Boolean = true) = ConstArray(it.map { it.name }.toTypedArray())
    @ExportMessage
    @Throws(UnsupportedMessageException::class)
    fun invokeMember(member: String, arguments: Array<Any?>): Any {
        return when (member) {
            "leakContext" -> env.asGuestValue(ctx)
            else -> throw UnsupportedMessageException.create()
        }
    }
    @ExportMessage
    fun isMemberInvocable(member: String) = member == "leakContext"
    @ExportMessage
    fun isScope(): Boolean = true
    @ExportMessage
    fun hasScopeParent() = false
    @ExportMessage
    @Throws(UnsupportedMessageException::class)
    fun getScopeParent(): Any = UnsupportedMessageException.create()
    @ExportMessage
    fun hasLanguage() = true
    @ExportMessage
    fun getLanguage(): Class<TruffleLanguage<*>> = lang.javaClass
    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = "MontunoScope"
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class ConstArray(val it: Array<Any>) : TruffleObject {
    @ExportMessage
    fun hasArrayElements() = true
    @ExportMessage
    fun getArraySize() = it.size
    @ExportMessage
    fun isArrayElementReadable(i: Long) = i < it.size
    @ExportMessage
    fun isArrayElementModifiable(i: Long) = false
    @ExportMessage
    fun isArrayElementInsertable(i: Long) = false
    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(i: Long): Any =
        if (i < it.size) it[i.toInt()] else throw InvalidArrayIndexException.create(i)
    @ExportMessage
    @Throws(UnsupportedMessageException::class)
    fun writeArrayElement(i: Long, o: Any): Any = throw UnsupportedMessageException.create()
}
