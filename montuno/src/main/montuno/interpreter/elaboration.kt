package montuno.interpreter

import montuno.common.*
import montuno.syntax.*

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
        .map { it.first to it.second }
        .map { (_, vSp) -> if (vSp is VLocal) vSp.head.it else -1 }.toTypedArray()
    if (renOverlap contentEquals spineLocal) {
        return (lvl - renOverlap.size to Renaming(ren.it.copyOfRange(0, mid))) to v.replaceSpine(VSpine())
    }
    return (lvl to ren) to v
}

fun Term.lams(lvl: Lvl, names: List<String>, ren: Renaming): Term {
    var t = this
    for ((x, _) in ren.it) {
        t = TLam(names[x.toIx(lvl).it], Icit.Expl, t)
    }
    return t
}

data class ErrRef(var err: FlexRigidError? = null)
data class ScopeCheckState(val occurs: Meta, val errRef: ErrRef?, val shift: Int)
@Throws(FlexRigidError::class)
fun Val.quoteSolution(topLvl: Lvl, names: List<String>, occurs: Meta, renL: Pair<Lvl, Renaming>, errRef: ErrRef?): Term {
    val (renl, ren) = renL
    return scopeCheck(ScopeCheckState(occurs, errRef, topLvl.it - renl.it), topLvl, ren).lams(topLvl, names, ren)
}
fun Term.scopeCheckSp(state: ScopeCheckState, lvl: Lvl, ren: Renaming, spine: VSpine): Term {
    return spine.it.fold(this) { l, it -> TApp(it.first, l, it.second.scopeCheck(state, lvl, ren)) }
}
fun Val.scopeCheck(state: ScopeCheckState, lvl: Lvl, ren: Renaming): Term = when (val v = force(false)) {
    VUnit -> TU
    VIrrelevant -> TIrrelevant
    is VNat -> TNat(v.n)
    is VFun -> TFun(v.a.scopeCheck(state, lvl, ren), v.b.scopeCheck(state, lvl, ren))
    is VLam -> TLam(v.n, v.icit, v.inst(VLocal(lvl)).scopeCheck(state,lvl + 1, ren + (lvl to lvl - state.shift)))
    is VPi -> TPi(v.n, v.icit, v.ty.scopeCheck(state, lvl, ren),
        v.inst(VLocal(lvl)).scopeCheck(state, lvl + 1, ren + (lvl to lvl - state.shift)))
    is VTop -> TTop(v.head, v.slot).scopeCheckSp(state, lvl, ren, v.spine)
    is VLocal -> {
        val x = ren.apply(v.head)
        if (x == null) {
            val err = FlexRigidError(Rigidity.Flex)
            if (state.errRef == null) throw err else state.errRef.err = err
            TIrrelevant
        }
        else TLocal(x.toIx(lvl) - state.shift).scopeCheckSp(state, lvl, ren, v.spine)
    }
    is VMeta -> when {
        v.head == state.occurs -> {
            val err = FlexRigidError(Rigidity.Flex)
            if (state.errRef == null) throw err else state.errRef.err = err
            TIrrelevant
        }
        MontunoPure.top[v.head].solved ->
            if (state.occurs.i == v.head.i) throw FlexRigidError(Rigidity.Flex)
            else TMeta(v.head, v.slot).scopeCheckSp(state, lvl, ren, v.spine)
        else -> TMeta(v.head, v.slot).scopeCheckSp(state, lvl, ren, v.spine)
    }
}

const val unfoldLimit = 2

@Throws(FlexRigidError::class)
fun LocalContext.unifySp(lvl: Lvl, unfold: Int, names: List<String>, r: Rigidity, a: VSpine, b: VSpine) {
    if (a.it.size != b.it.size) throw FlexRigidError(r, "spine length mismatch")
    for ((aa, bb) in a.it.zip(b.it)) {
        if (aa.first != bb.first) throw FlexRigidError(r, "icity mismatch")
        unify(lvl, unfold, names, r, aa.second, bb.second)
    }
}

@Throws(FlexRigidError::class)
fun LocalContext.unify(lvl: Lvl, unfold: Int, names: List<String>, r: Rigidity, a: Val, b: Val) {
    val v = a.force(true)
    val w = b.force(true)
    val local = VLocal(lvl)
    when {
        v is VIrrelevant || w is VIrrelevant -> {}
        v is VUnit && w is VUnit -> {}
        v is VFun && w is VFun -> {
            unify(lvl, unfold, names, r, v.a, w.a)
            unify(lvl, unfold, names, r, v.b, w.b)
        }

        v is VLam && w is VLam -> unify(lvl + 1, unfold, names + v.n, r, v.inst(local), w.inst(local))
        v is VLam -> unify(lvl + 1, unfold, names + v.n, r, v.inst(local), w.app(v.icit, local))
        w is VLam -> unify(lvl + 1, unfold, names + w.n, r, w.inst(local), v.app(w.icit, local))

        v is VPi && w is VPi && v.icit == w.icit -> {
            unify(lvl, unfold, names, r, v.ty, w.ty)
            unify(lvl + 1, unfold, names, r, v.inst(local), w.inst(local))
        }
        v is VPi && w is VFun -> {
            unify(lvl, unfold, names, r, v.ty, w.a)
            unify(lvl + 1, unfold, names + v.n, r, v.inst(local), w.b)
        }
        w is VPi && v is VFun -> {
            unify(lvl, unfold, names, r, w.ty, v.a)
            unify(lvl + 1, unfold, names + w.n, r, w.inst(local), v.b)
        }

        v is VLocal && w is VLocal && v.head == w.head -> unifySp(lvl, unfold, names, r, v.spine, w.spine)

        v is VMeta && w is VMeta && v.head == w.head -> {
            val rSp = r.meld(top.rigidity(v.head)).meld(top.rigidity(w.head))
            unifySp(lvl, unfold, names, rSp, v.spine, w.spine)
        }
        v is VMeta && !top[v.head].solved && r == Rigidity.Rigid -> solve(Rigidity.Flex, lvl, names, v.head, v.spine, w)
        w is VMeta && !top[w.head].solved && r == Rigidity.Rigid -> solve(Rigidity.Flex, lvl, names, w.head, w.spine, v)
        v is VMeta && top[v.head].solved ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, v.appSpine(v.spine), w)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")
        w is VMeta && top[w.head].solved ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, w.appSpine(w.spine), v)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")

        v is VTop && w is VTop && v.head == w.head -> {
            val rSp = r.meld(top.rigidity(v.head)).meld(top.rigidity(w.head))
            unifySp(lvl, unfold, names, rSp, v.spine, w.spine)
        }
        v is VTop && top[v.head].defn != null ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, v.appSpine(v.spine), w)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")
        w is VTop && top[w.head].defn != null ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, w.appSpine(w.spine), v)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")

        else -> throw FlexRigidError(r, "failed to unify:\n$v\n$w")
    }
}

fun VSpine.check(mode: Rigidity): Pair<Lvl, Renaming> {
    val s = it.size
    val x = mutableListOf<Pair<Lvl, Lvl>>()
    for ((i, v) in it.reversed().withIndex()) {
        val vsp = v.second.force(false)
        if (vsp !is VLocal) throw FlexRigidError(mode,"not local")
        x.add(vsp.head to Lvl(s - i - 1))
    }
    return Lvl(it.size) to Renaming(x.toTypedArray())
}
fun VSpine.strip(topLvl: Lvl, lvl: Lvl): Boolean {
    //TODO: maybe in reverse?
    var l = lvl.it
    for ((_, v) in it.reversed()) {
        if (v !is VLocal || v.head.it != l - 1) return false
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
        v is VLam -> v.inst(VLocal(lvl)).checkSolution(occurs, shift, topLvl, lvl + 1, ren + (lvl to lvl - shift))
        v is VMeta && v.head == occurs && v.spine.strip(topLvl, lvl) -> true
        else -> {
            v.scopeCheck(occurs, shift, topLvl, lvl, ren)
            false
        }
    }
}
fun Val.scopeCheck(occurs: Meta, shift: Int, topLvl: Lvl, lvl: Lvl, ren: Renaming) {
    val local = VLocal(lvl)
    when (val v = force(false)) {
        is VUnit -> {}
        is VIrrelevant -> {}
        is VFun -> { v.a.scopeCheck(occurs, shift, topLvl, lvl, ren); v.b.scopeCheck(occurs, shift, topLvl, lvl, ren) }
        is VPi -> { v.ty.scopeCheck(occurs, shift, topLvl, lvl, ren); v.inst(local).scopeCheck(occurs, shift, topLvl, lvl + 1, ren + (lvl to lvl - shift)) }
        is VLam -> v.inst(local).scopeCheck(occurs, shift, topLvl, lvl + 1, ren + (lvl to lvl - shift))
        is VTop -> v.spine.it.forEach { it.second.scopeCheck(occurs, shift, topLvl, lvl, ren) }
        is VMeta -> {
            if (v.head == occurs) throw UnifyError("SEOccurs")
            v.spine.it.forEach { it.second.scopeCheck(occurs, shift, topLvl, lvl, ren) }
        }
        is VLocal -> {
            if (ren.apply(v.head) == null) throw UnifyError("SEScope")
            v.spine.it.forEach { it.second.scopeCheck(occurs, shift, topLvl, lvl, ren) }
        }
    }
}

@Throws(ElabError::class)
fun LocalContext.unify(a: Val, b: Val) {
    try {
        unify(env.lvl, unfoldLimit, env.names, Rigidity.Rigid, a, b)
    } catch (fr: FlexRigidError) {
        when (fr.rigidity) {
            Rigidity.Rigid -> throw fr
            Rigidity.Flex -> gUnify(env.lvl, env.names, a, b)
        }
    }
}

fun LocalContext.gUnify(lvl: Lvl, names: List<String>, a: Val, b: Val) {
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

        v is VLam && w is VLam -> gUnify(lvl + 1, names + v.n, v.inst(local), w.inst(local))
        v is VLam -> gUnify(lvl + 1, names + v.n, v.inst(local), w.app(v.icit, local))
        w is VLam -> gUnify(lvl + 1, names + w.n, w.inst(local), v.app(w.icit, local))

        v is VPi && w is VPi && v.n == w.n -> {
            gUnify(lvl, names, v.ty, w.ty)
            gUnify(lvl + 1, names + v.n, v.inst(local), w.inst(local))
        }
        v is VPi && w is VFun -> {
            gUnify(lvl, names, v.ty, w.a)
            gUnify(lvl + 1, names + v.n, v.inst(local), w.b)
        }
        w is VPi && v is VFun -> {
            gUnify(lvl, names, w.ty, v.a)
            gUnify(lvl + 1, names + w.n, w.inst(local), v.b)
        }

        v is VFun && w is VFun -> {
            gUnify(lvl, names, v.a, w.a)
            gUnify(lvl, names, v.b, w.b)
        }

        v is VTop && w is VTop && v.head == w.head -> unifySp(lvl, names, v.spine, w.spine)
        v is VLocal && w is VLocal && v.head == w.head -> unifySp(lvl, names, v.spine, w.spine)

        else -> throw UnifyError("failed to unify in glued mode")
    }
}

fun LocalContext.unifySp(lvl: Lvl, names: List<String>, va: VSpine, vb: VSpine) {
    if (va.it.size != vb.it.size) throw UnifyError("spines differ")
    for (i in va.it.indices) {
        if (va.it[i].first != vb.it[i].first) throw UnifyError("spines differ")
        gUnify(lvl, names, va.it[i].second, vb.it[i].second)
    }
}
//
//fun LocalContext.solve(lvl: Lvl, names: List<String>, occurs: Meta, vsp: VSpine, v: Val) {
//    val (lvlRen, vNew) = contract(vsp.check(), v)
//    MontunoPure.top[occurs] = vNew.quoteSolution(lvl, names, occurs, lvlRen, null)
//}
fun LocalContext.solve(mode: Rigidity, lvl: Lvl, names: List<String>, occurs: Meta, sp: VSpine, v: Val) {
    val (renC, vC) = contract(sp.check(mode), v)
    val errRef = ErrRef()
    val rhs = vC.quoteSolution(lvl, names, occurs, renC, errRef)
    val err = errRef.err
    when {
        err == null -> top[occurs] = rhs
        err.rigidity == Rigidity.Rigid -> throw err
        else -> if (!v.checkSolution(occurs, this.env.lvl, renC)) top[occurs] = rhs
    }
}


fun check(ctx: LocalContext, t: PreTerm, want: Val): Term {
    ctx.top.loc = t.loc
    val v = ctx.force(want,true)
    return when {
        t is RHole -> ctx.newMeta()
        t is RLet -> {
            val a = ctx.check(t.type, VUnit)
            val va = ctx.eval(a)
            val tm = ctx.check(t.defn, va)
            val vt = ctx.eval(tm)
            val u = ctx.define(t.loc, t.n, va, vt).check(t.body, want)
            TLet(t.n, a, tm, u)
        }
        t is RLam && v is VPi && t.ni.match(v) ->
            TLam(t.n, v.icit, ctx.bind(t.loc, t.n, false, v.ty).check(t.body, v.inst(VLocal(ctx.env.lvl))))
        t is RLam && v is VFun && t.ni is NIExpl ->
            TLam(t.n, Icit.Expl, ctx.bind(t.loc, t.n, false, v.a).check(t.body, v.b))
        v is VPi && v.icit == Icit.Impl ->
            TLam(v.n, Icit.Impl, ctx.bind(t.loc, v.n, true, v.ty).check(t, v.inst(VLocal(ctx.env.lvl))))
        else -> {
            val (tt, has) = ctx.infer(MetaInsertion.Yes, t)
            try {
                println("$has ?= $want")
                ctx.unify(has, want)
            } catch (e: ElabError) {
                throw ElabError(ctx.top.loc, "Failed to unify $has and $want")
            }
            tt
        }
    }
}
fun NameOrIcit.match(v: VPi): Boolean = when (this) {
    NIImpl -> v.icit == Icit.Impl
    NIExpl -> v.icit == Icit.Expl
    is NIName -> v.n == n
}

fun insertMetas(ctx: LocalContext, mi: MetaInsertion, c: Pair<Term, Val>): Pair<Term, Val> {
    var (t, va) = c
    when (mi) {
        MetaInsertion.No -> {}
        MetaInsertion.Yes -> {
            var vaf = va.force(false)
            while (vaf is VPi && vaf.icit == Icit.Impl) {
                val m = ctx.newMeta()
                t = TApp(Icit.Impl, t, m)
                va = vaf.inst(ctx.eval(m))
                vaf = va.force(false)
            }
        }
        is MetaInsertion.UntilName -> {
            var vaf = va.force(false)
            while (vaf is VPi && vaf.icit == Icit.Impl) {
                if (vaf.n == mi.n) {
                    return t to va
                }
                val m = ctx.newMeta()
                t = TApp(Icit.Impl, t, m)
                va = vaf.inst(ctx.eval(m))
                vaf = va.force(false)
            }
            throw ElabError(ctx.top.loc, "No named arg ${mi.n}")
        }
    }
    return t to va
}

fun infer(ctx: LocalContext, mi: MetaInsertion, r: PreTerm): Pair<Term, Val> {
    ctx.top.loc = r.loc
    val terms = ctx.top.termFactory
    val vals = ctx.top.valFactory
    return when (r) {
        is RU -> terms.unit() to vals.unit()
        is RNat -> terms.nat(r.n) to ctx.eval(ctx.inferVar("Nat").first)
        is RVar -> insertMetas(ctx, mi, ctx.inferVar(r.n))
        is RStopMeta -> ctx.infer(MetaInsertion.No, r.body)
        is RForeign -> {
            val a = ctx.check(r.type, VUnit)
            TForeign(r.lang, r.eval, a) to ctx.eval(a)
        }
        is RHole -> {
            val m1 = ctx.newMeta()
            val m2 = ctx.newMeta()
            m1 to ctx.eval(m2)
        }
        is RPi -> {
            val a = ctx.check(r.type, VUnit)
            val b = ctx.bind(r.loc, r.n, false, ctx.eval(a)).check(r.body, VUnit)
            TPi(r.n, r.icit, a, b) to VUnit
        }
        is RFun -> TFun(ctx.check(r.l, VUnit), ctx.check(r.r, VUnit)) to VUnit
        is RLet -> {
            val a = ctx.check(r.type, VUnit)
            val gva = ctx.eval(a)
            val t = ctx.check(r.defn, gva)
            val gvt = ctx.eval(t)
            val (u, gvb) = ctx.define(r.loc, r.n, gvt, gva).infer(mi, r.body)
            TLet(r.n, a, t, u) to gvb
        }
        is RApp -> {
            val ins = when (r.ni) {
                is NIName -> MetaInsertion.UntilName(r.ni.n)
                NIImpl -> MetaInsertion.No
                NIExpl -> MetaInsertion.Yes
            }
            val (t, va) = ctx.infer(ins, r.rator)
            insertMetas(ctx, mi, when (val v = ctx.force(va, true)) {
                is VPi -> {
                    when {
                        r.ni is NIExpl && v.icit != Icit.Expl -> { ctx.top.loc = r.loc; throw ElabError(r.loc, "AppIcit") }
                        r.ni is NIImpl && v.icit != Icit.Impl -> { ctx.top.loc = r.loc; throw ElabError(r.loc, "AppIcit") }
                    }
                    val u = ctx.check(r.rand, v.ty)
                    TApp(v.icit, t, u) to v.inst(ctx.eval(u))
                }
                is VFun -> {
                    if (r.ni !is NIExpl) throw ElabError(r.loc, "Icit mismatch")
                    val u = ctx.check(r.rand, v.a)
                    TApp(Icit.Expl, t, u) to v.b
                }
                else -> throw ElabError(r.loc, "function expected, instead got $v")
            })
        }
        is RLam -> {
            val icit = when (r.ni) {
                NIImpl -> Icit.Impl
                NIExpl -> Icit.Expl
                is NIName -> throw ElabError(r.loc, "named lambda")
            }
            val va = ctx.eval(ctx.newMeta())
            val (t, vb) = ctx.bind(r.loc, r.n, false, va).infer(MetaInsertion.Yes, r.body)
            val b = ctx.quote(vb, false, ctx.env.lvl + 1)
            insertMetas(ctx, mi, TLam(r.n, icit, t) to VPi(r.n, icit, va, VCl(ctx.env.vals, b)))
        }
    }
}
