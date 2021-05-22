package montuno.interpreter

import montuno.*
import montuno.syntax.Icit
import java.util.*

inline class Renaming(val it: Array<Pair<Lvl, Lvl>>) {
    fun apply(x: Lvl): Lvl? = it.find { it.first == x }?.second
    operator fun plus(x: Pair<Lvl, Lvl>) = Renaming(it.plus(x))
}

inline class LvlSet(val it: BitSet) {
    fun disjoint(r: Renaming): Boolean = r.it.any { this.it[it.first.it] }
}

fun contract(lvlRen: Pair<Lvl, Renaming>, v: Val): Pair<Pair<Lvl, Renaming>, Val> {
    val (lvl, ren) = lvlRen
    val spine = when (v) {
        is VLocal -> v.spine
        is VTop -> v.spine
        is VMeta -> v.spine
        else -> return (lvl to ren) to v
    }
    if (spine.it.size <= ren.it.size) return (lvl to ren) to v
    val mid = ren.it.size - spine.it.size
    val renOverlap = ren.it.copyOfRange(mid, ren.it.size).map { it.first.it }.toTypedArray()
    val spineLocal = spine.it
        .map { sp -> if (sp is SApp && sp.v is VLocal) sp.v.head.it else -1 }.toTypedArray()
    if (renOverlap contentEquals spineLocal) {
        return (lvl - renOverlap.size to Renaming(ren.it.copyOfRange(0, mid))) to v.replaceSpine(VSpine())
    }
    return (lvl to ren) to v
}

fun Term.lams(lvl: Lvl, names: List<String?>, ren: Renaming): Term {
    var t = this
    for ((x, _) in ren.it) {
        t = TLam(names[x.toIx(lvl).it], Icit.Expl, todo, t)
    }
    return t
}

data class ErrRef(var err: FlexRigidError? = null)
data class ScopeCheckState(val occurs: Meta, val errRef: ErrRef?, val shift: Int)
@Throws(FlexRigidError::class)
fun Val.quoteSolution(topLvl: Lvl, names: List<String?>, occurs: Meta, renL: Pair<Lvl, Renaming>, errRef: ErrRef?, v: Val): Term {
    val (renl, ren) = renL
    val res = rename(ScopeCheckState(occurs, errRef, topLvl.it - renl.it), topLvl, ren)
    return res.lams(topLvl, names, ren)
}
fun Term.renameSp(state: ScopeCheckState, lvl: Lvl, ren: Renaming, spine: VSpine): Term {
    return spine.it.reversed().fold(this) { l, it -> when (it) {
        SProj1 -> TProj1(l)
        SProj2 -> TProj2(l)
        is SProjF -> TProjF(it.n, l, it.i)
        is SApp -> TApp(it.icit, l, it.v.rename(state, lvl, ren))
    } }
}
fun Val.rename(state: ScopeCheckState, lvl: Lvl, ren: Renaming): Term = when (val v = force(false)) {
    VUnit -> TUnit
    VIrrelevant -> TIrrelevant
    is VNat -> TNat(v.n)
    is VLam -> TLam(v.name, v.icit, v.bound.rename(state, lvl, ren), v.closure.inst(VLocal(lvl)).rename(state,lvl + 1, ren + (lvl to lvl - state.shift)))
    is VPi -> TPi(v.name, v.icit, v.bound.rename(state, lvl, ren),
        v.closure.inst(VLocal(lvl)).rename(state, lvl + 1, ren + (lvl to lvl - state.shift)))
    is VTop -> TTop(v.head, v.slot).renameSp(state, lvl, ren, v.spine)
    is VLocal -> {
        val x = ren.apply(v.head)
        if (x == null) {
            val err = FlexRigidError(Rigidity.Flex)
            if (state.errRef == null) throw err else state.errRef.err = err
            TIrrelevant
        }
        else TLocal(x.toIx(lvl) - state.shift).renameSp(state, lvl, ren, v.spine)
    }
    is VMeta -> when {
        v.head == state.occurs -> {
            val err = FlexRigidError(Rigidity.Flex)
            if (state.errRef == null) throw err else state.errRef.err = err
            TIrrelevant
        }
        v.slot.solved ->
            if (state.occurs.i == v.head.i) throw FlexRigidError(Rigidity.Flex)
            else TMeta(v.head, v.slot).renameSp(state, lvl, ren, v.spine)
        else -> TMeta(v.head, v.slot).renameSp(state, lvl, ren, v.spine)
    }
    is VPair -> TODO()
    is VSg -> TODO()
}

fun VSpine.check(mode: Rigidity): Pair<Lvl, Renaming> {
    val s = it.size
    val x = mutableListOf<Pair<Lvl, Lvl>>()
    for ((i, v) in it.reversed().withIndex()) {
        if (v !is SApp) continue
        val vsp = v.v.force(false)
        if (vsp !is VLocal) throw FlexRigidError(mode,"not local")
        x.add(vsp.head to Lvl(s - i - 1))
    }
    return Lvl(it.size) to Renaming(x.toTypedArray())
}
fun VSpine.strip(topLvl: Lvl, lvl: Lvl): Boolean {
    //TODO: maybe in reverse?
    var l = lvl.it
    for (sp in it.reversed()) {
        if (sp !is SApp || sp.v !is VLocal || sp.v.head.it != l - 1) return false
        l--
    }
    return l == topLvl.it
}

fun Val.checkSolution(occurs: Meta, topLvl: Lvl, renLvl: Pair<Lvl, Renaming>): Boolean {
    val (renl, ren) = renLvl
    return checkSolution(occurs, topLvl.it - renl.it, topLvl, topLvl, ren)
}
fun Val.checkSolution(occurs: Meta, shift: Int, topLvl: Lvl, lvl: Lvl, ren: Renaming): Boolean {
    val v = force(false)
    return when {
        v is VLam -> v.closure.inst(VLocal(lvl)).checkSolution(occurs, shift, topLvl, lvl + 1, ren + (lvl to lvl - shift))
        v is VMeta && v.head == occurs && v.spine.strip(topLvl, lvl) -> true
        else -> {
            v.rename(occurs, shift, topLvl, lvl, ren)
            false
        }
    }
}
fun VSpine.rename(occurs: Meta, shift: Int, topLvl: Lvl, lvl: Lvl, ren: Renaming) {
    for (sp in it) if (sp is SApp) {
        sp.v.rename(occurs, shift, topLvl, lvl, ren)
    }
}
fun Val.rename(occurs: Meta, shift: Int, topLvl: Lvl, lvl: Lvl, ren: Renaming) {
    val local = VLocal(lvl)
    when (val v = force(false)) {
        is VUnit -> {}
        is VIrrelevant -> {}
        is VPi -> {
            v.bound.rename(occurs, shift, topLvl, lvl, ren)
            v.closure.inst(local).rename(occurs, shift, topLvl, lvl + 1, ren + (lvl to lvl - shift))
        }
        is VLam -> v.closure.inst(local).rename(occurs, shift, topLvl, lvl + 1, ren + (lvl to lvl - shift))
        is VTop -> v.spine.rename(occurs, shift, topLvl, lvl, ren)
        is VMeta -> {
            if (v.head == occurs) throw UnifyError("SEOccurs")
            v.spine.rename(occurs, shift, topLvl, lvl, ren)
        }
        is VLocal -> {
            if (ren.apply(v.head) == null) throw UnifyError("SEScope")
            v.spine.rename(occurs, shift, topLvl, lvl, ren)
        }
    }
}

@Throws(ElabError::class)
fun LocalContext.unify(has: Val, want: Val) {
    try {
        unify(env.lvl, unfoldLimit, env.names, Rigidity.Rigid, has, want)
    } catch (fr: FlexRigidError) {
        when (fr.rigidity) {
            Rigidity.Rigid -> throw ElabError(ctx.loc, "Failed to unify $has and $want")
            Rigidity.Flex -> gUnify(env.lvl, env.names, has, want)
        }
    }
}

const val unfoldLimit = 2

@Throws(FlexRigidError::class)
fun LocalContext.unifySp(lvl: Lvl, unfold: Int, names: List<String?>, r: Rigidity, a: VSpine, b: VSpine) {
    if (a.it.size != b.it.size) throw FlexRigidError(r, "spines differ")
    for ((aa, bb) in a.it.zip(b.it)) when {
        aa == SProj1 && bb == SProj1 -> {}
        aa == SProj2 && bb == SProj2 -> {}
        aa is SProjF && bb is SProjF -> if (aa.i != bb.i) throw FlexRigidError(r, "spines differ")
        aa is SApp && bb is SApp -> unify(lvl, unfold, names, r, aa.v, bb.v)
        else -> throw FlexRigidError(r, "spines differ")
    }
}

fun LocalContext.unifySp(lvl: Lvl, names: List<String?>, a: VSpine, b: VSpine) {
    if (a.it.size != b.it.size) throw UnifyError("spines differ")
    for ((aa, bb) in a.it.zip(b.it)) when {
        aa == SProj1 && bb == SProj1 -> {}
        aa == SProj2 && bb == SProj2 -> {}
        aa is SProjF && bb is SProjF -> if (aa.i != bb.i) throw UnifyError("spines differ")
        aa is SApp && bb is SApp -> gUnify(lvl, names, aa.v, bb.v)
        else -> throw UnifyError("spines differ")
    }
}

@Throws(FlexRigidError::class)
fun LocalContext.unify(lvl: Lvl, unfold: Int, names: List<String?>, r: Rigidity, a: Val, b: Val) {
    val v = a.force(true)
    val w = b.force(true)
    val local = VLocal(lvl)
    when {
        v is VIrrelevant || w is VIrrelevant -> {}
        v is VUnit && w is VUnit -> {}

        v is VLam && w is VLam -> unify(lvl + 1, unfold, names + v.name, r, v.closure.inst(local), w.closure.inst(local))
        v is VLam -> unify(lvl + 1, unfold, names + v.name, r, v.closure.inst(local), w.app(v.icit, local))
        w is VLam -> unify(lvl + 1, unfold, names + w.name, r, w.closure.inst(local), v.app(w.icit, local))

        v is VPi && w is VPi && v.icit == w.icit -> {
            unify(lvl, unfold, names, r, v.bound, w.bound)
            unify(lvl + 1, unfold, names, r, v.closure.inst(local), w.closure.inst(local))
        }

        v is VLocal && w is VLocal && v.head == w.head -> unifySp(lvl, unfold, names, r, v.spine, w.spine)

        v is VMeta && w is VMeta && v.head == w.head -> {
            val rSp = r.meld(v.slot.rigidity).meld(w.slot.rigidity)
            unifySp(lvl, unfold, names, rSp, v.spine, w.spine)
        }
        v is VMeta && !v.slot.solved && r == Rigidity.Rigid -> solve(Rigidity.Flex, lvl, names, v.head, v.spine, w)
        w is VMeta && !w.slot.solved && r == Rigidity.Rigid -> solve(Rigidity.Flex, lvl, names, w.head, w.spine, v)
        v is VMeta && v.slot.solved ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, v.spine.applyTo(v), w)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")
        w is VMeta && w.slot.solved ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, w.spine.applyTo(w), v)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")

        v is VTop && w is VTop && v.head == w.head -> {
            val rSp = r.meld(v.slot.rigidity).meld(w.slot.rigidity)
            unifySp(lvl, unfold, names, rSp, v.spine, w.spine)
        }
        v is VTop && v.slot.defn != null ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, v.spine.applyTo(v), w)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")
        w is VTop && w.slot.defn != null ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, w.spine.applyTo(w), v)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")

        else -> throw FlexRigidError(r, "failed to unify:\n$v\n$w")
    }
}

fun LocalContext.gUnify(lvl: Lvl, names: List<String?>, a: Val, b: Val) {
    val v = a.force(true)
    val w = b.force(true)
    val local = VLocal(lvl)
    when {
        v is VUnit && w is VUnit -> {}

        v is VMeta && w is VMeta -> when {
            v.head < w.head -> solve(Rigidity.Rigid, lvl, names, w.head, w.spine, a)
            v.head > w.head -> solve(Rigidity.Rigid, lvl, names, v.head, v.spine, b)
            else -> unifySp(lvl, names, v.spine, w.spine)
        }

        v is VMeta -> solve(Rigidity.Rigid, lvl, names, v.head, v.spine, b)
        w is VMeta -> solve(Rigidity.Rigid, lvl, names, w.head, w.spine, a)

        v is VLam && w is VLam -> gUnify(lvl + 1, names + v.name, v.closure.inst(local), w.closure.inst(local))
        v is VLam -> gUnify(lvl + 1, names + v.name, v.closure.inst(local), w.app(v.icit, local))
        w is VLam -> gUnify(lvl + 1, names + w.name, w.closure.inst(local), v.app(w.icit, local))

        v is VPi && w is VPi && v.name == w.name -> {
            gUnify(lvl, names, v.bound, w.bound)
            gUnify(lvl + 1, names + v.name, v.closure.inst(local), w.closure.inst(local))
        }

        v is VTop && w is VTop && v.head == w.head -> unifySp(lvl, names, v.spine, w.spine)
        v is VLocal && w is VLocal && v.head == w.head -> unifySp(lvl, names, v.spine, w.spine)

        else -> throw UnifyError("failed to unify in glued mode")
    }
}

//
//fun LocalContext.solve(lvl: Lvl, names: List<String>, occurs: Meta, vsp: VSpine, v: Val) {
//    val (lvlRen, vNew) = contract(vsp.check(), v)
//    MontunoPure.top[occurs] = vNew.quoteSolution(lvl, names, occurs, lvlRen, null)
//}
fun LocalContext.solve(mode: Rigidity, lvl: Lvl, names: List<String?>, occurs: Meta, sp: VSpine, v: Val) {
    val (renC, vC) = contract(sp.check(mode), v)
    val errRef = ErrRef()
    val rhs = vC.quoteSolution(lvl, names, occurs, renC, errRef, v)
    val err = errRef.err
    if (err != null) {
        if (err.rigidity == Rigidity.Rigid) throw err
        if (v.checkSolution(occurs, this.env.lvl, renC)) return
    }
    ctx.compileMeta(occurs, rhs)
}
