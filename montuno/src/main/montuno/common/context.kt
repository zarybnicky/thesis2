package montuno.common

import com.oracle.truffle.api.TruffleLanguage
import montuno.*
import montuno.syntax.Loc

interface ITerm {
    fun isUnfoldable(): Boolean
}
interface ILocalEnv<V> {
    fun localBind(loc: Loc, n: String, inserted: Boolean, gv: V): ILocalEnv<V>
    fun localBindSrc(loc: Loc, n: String, gv: V): ILocalEnv<V>
    fun localBindIns(loc: Loc, n: String, gv: V): ILocalEnv<V>
    fun localDefine(loc: Loc, n: String, gv: V, gvty: V): ILocalEnv<V>
}

// abstract fun eval(t: T): V

abstract class MontunoContext<T : ITerm, V>(val lang: TruffleLanguage<*>, env: TruffleLanguage.Env) {
    val topScope = TopLevelScope(lang, this, env)
    var metas: MutableList<MutableList<MetaEntry<T, V>>> = mutableListOf()
    var loc: Loc = Loc.Unavailable
    var ntbl = NameTable()

    fun reset() {
        topScope.entries.removeAll { true }
        metas = mutableListOf()
        loc = Loc.Unavailable
        ntbl = NameTable()
    }

    //abstract fun localContext(ntbl: NameTable): ILocalEnv<V>

    fun rigidity(lvl: Lvl) = if (topScope.entries[lvl.it].defn == null) Rigidity.Rigid else Rigidity.Flex
    fun rigidity(meta: Meta) = if (metas[meta.i][meta.j] is MetaUnsolved) Rigidity.Rigid else Rigidity.Flex

    operator fun get(lvl: Lvl) = topScope.entries[lvl.it]
    operator fun get(meta: Meta) = metas[meta.i][meta.j]
    operator fun set(meta: Meta, m: MetaEntry<T, V>) {
        val (i, j) = meta
        assert(metas[i].size == j)
        metas[i].add(m)
    }
}