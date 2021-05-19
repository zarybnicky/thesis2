package montuno.truffle

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import montuno.*
import montuno.common.*
import montuno.syntax.PreTerm

class MontunoTruffleContext(lang: TruffleLanguage<*>, env: TruffleLanguage.Env) : MontunoContext<TermRootNode, Val>(lang, env) {
    fun printElaborated() = topScope.entries.forEach { println("${it.name} : todo") }

    fun getTopTerm(lvl: Lvl): TermRootNode = topScope.entries[lvl.it].defn?.first!!
    fun getTopVal(lvl: Lvl): Any = when (val top = topScope.entries[lvl.it].defn) {
        null -> VNe(HTop(lvl), emptyArray())
        else -> top.first.callTarget.call()
    }
    fun getMetaForce(meta: Meta): Any = when (val m = metas[meta.i][meta.j]) {
        is MetaSolved -> m.v
        else -> VNe(HMeta(meta), emptyArray())
    }
}

data class LocalContext(
    val ntbl: NameTable = NameTable(),
    val fd: FrameDescriptor = FrameDescriptor(),
    val fdLvl: Lvl = Lvl(0)
) {
    fun infer(mi: MetaInsertion, pre: PreTerm): Pair<Term, Val> = todo
}
