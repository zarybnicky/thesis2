package montuno.interpreter.meta

import montuno.interpreter.*
import montuno.syntax.*
import kotlin.Throws
import kotlin.math.max

fun Term.isUnfoldable(): Boolean = when (this) {
    is TLocal -> true
    is TMeta -> true
    is TTop -> true
    is TU -> true
    is TLam -> tm.isUnfoldable()
    else -> false
}

fun contract(lvlRen: Pair<Lvl, Renaming>, v: Val): Pair<Pair<Lvl, Renaming>, Val> {
    val (lvl, ren) = lvlRen
    // TODO: check whether this works at all???
    if (v !is VNe || v.spine.it.size <= ren.it.size) return (lvl to ren) to v
    val mid = ren.it.size - v.spine.it.size
    val renOverlap = ren.it.copyOfRange(mid, ren.it.size).map { it.first.it }.toTypedArray()
    val spineLocal = v.spine.it
        .map { it.first to it.second.value }
        .map { (_, vSp) -> if (vSp is VNe && vSp.head is HLocal) vSp.head.ix.it else -1 }.toTypedArray()
    if (renOverlap contentEquals spineLocal) {
        return (Lvl(lvl.it - renOverlap.size) to Renaming(ren.it.copyOfRange(0, mid))) to VNe(v.head, emptyVSpine)
    }
    return (lvl to ren) to v
}

fun Term.lams(lvl: Lvl, names: Array<String>, ren: Renaming): Term {
    var t = this
    for ((x, _) in ren.it) {
        t = TLam(names[lvl.it - x.it - 1], Icit.Expl, t)
    }
    return t
}

data class ErrRef(var err: FlexRigidError? = null)
data class ScopeCheckState(val top: TopContext, val occurs: Meta, val errRef: ErrRef?, val shift: Int)
@Throws(FlexRigidError::class)
fun Val.quoteSolution(top: TopContext, topLvl: Lvl, names: Array<String>, occurs: Meta, renL: Pair<Lvl, Renaming>, errRef: ErrRef?): Term {
    val (renl, ren) = renL
    return scopeCheck(ScopeCheckState(top, occurs, errRef, topLvl.it - renl.it), topLvl, ren).lams(topLvl, names, ren)
}
fun Val.scopeCheck(state: ScopeCheckState, lvl: Lvl, ren: Renaming): Term = when (val v = force(state.top)) {
    VU -> TU
    VIrrelevant -> TIrrelevant
    is VFun -> TFun(v.a.value.scopeCheck(state, lvl, ren), v.b.value.scopeCheck(state, lvl, ren))
    is VLam -> TLam(v.n, v.icit, v.cl.inst(state.top, lazyOf(vLocal(Ix(lvl.it)))).scopeCheck(state,lvl + 1, ren + (lvl to lvl - state.shift)))
    is VPi -> TPi(v.n, v.icit, v.ty.value.scopeCheck(state, lvl, ren),
        v.cl.inst(state.top, lazyOf(vLocal(Ix(lvl.it)))).scopeCheck(state, lvl + 1, ren + (lvl to lvl - state.shift)))
    is VNe -> {
        var root = when (v.head) {
            is HTop -> TTop(v.head.lvl)
            is HLocal -> {
                val x = ren.apply(Lvl(v.head.ix.it))
                if (x == -1) {
                    val err = FlexRigidError(Rigidity.Flex)
                    if (state.errRef == null) throw err else state.errRef.err = err
                    TIrrelevant
                }
                else TLocal(Ix(lvl.it - state.shift - x - 1))
            }
            is HMeta -> when {
                v.head.meta == state.occurs -> {
                    val err = FlexRigidError(Rigidity.Flex)
                    if (state.errRef == null) throw err else state.errRef.err = err
                    TIrrelevant
                }
                state.top[v.head.meta] is MetaSolved ->
                    if (state.occurs.i == v.head.meta.i) throw FlexRigidError(Rigidity.Flex)
                    else TMeta(v.head.meta)
                else -> TMeta(v.head.meta)
            }
        }
        for ((icit, vSp) in v.spine.it) {
            root = TApp(icit, root, vSp.value.scopeCheck(state, lvl, ren))
        }
        root
    }
}

const val unfoldLimit = 2

@Throws(FlexRigidError::class)
fun LocalContext.unifySp(lvl: Lvl, unfold: Int, names: Array<String>, r: Rigidity, a: VSpine, b: VSpine) {
    if (a.it.size != b.it.size) throw FlexRigidError(r, "spine length mismatch")
    for ((aa, bb) in a.it.zip(b.it)) {
        if (aa.first != bb.first) throw FlexRigidError(r, "icity mismatch")
        unify(lvl, unfold, names, r, aa.second.value, bb.second.value)
    }
}

fun LocalContext.unify(a: Val, b: Val) {
    unify(lvl, unfoldLimit, names, Rigidity.Rigid, a, b)
}

@Throws(FlexRigidError::class)
fun LocalContext.unify(lvl: Lvl, unfold: Int, names: Array<String>, r: Rigidity, a: Val, b: Val) {
    val v = a.force(top)
    val w = b.force(top)
    val local = lazyOf(vLocal(Ix(lvl.it)))
    when {
        v is VIrrelevant || w is VIrrelevant -> {}
        v is VU && w is VU -> {}
        v is VFun && w is VFun -> {
            unify(lvl, unfold, names, r, v.a.value, w.a.value)
            unify(lvl, unfold, names, r, v.b.value, w.b.value)
        }

        v is VLam && w is VLam -> unify(lvl + 1, unfold, names + v.n, r, v.cl.inst(top, local), w.cl.inst(top, local))
        v is VLam -> unify(lvl + 1, unfold, names + v.n, r, v.cl.inst(top, local), w.app(top, v.icit, local))
        w is VLam -> unify(lvl + 1, unfold, names + w.n, r, w.cl.inst(top, local), v.app(top, w.icit, local))

        v is VPi && w is VPi && v.icit == w.icit -> {
            unify(lvl, unfold, names, r, v.ty.value, w.ty.value)
            unify(lvl + 1, unfold, names, r, v.cl.inst(top, local), w.cl.inst(top, local))
        }
        v is VPi && w is VFun -> {
            unify(lvl, unfold, names, r, v.ty.value, w.a.value)
            unify(lvl + 1, unfold, names + v.n, r, v.cl.inst(top, local), w.b.value)
        }
        w is VPi && v is VFun -> {
            unify(lvl, unfold, names, r, w.ty.value, v.a.value)
            unify(lvl + 1, unfold, names + w.n, r, w.cl.inst(top, local), v.b.value)
        }

        v is VNe && w is VNe && v.head is HTop && w.head is HTop && v.head.lvl == w.head.lvl -> {
            val rSp = r.meld(top.rigidity(v.head.lvl)).meld(top.rigidity(w.head.lvl))
            unifySp(lvl, unfold, names, rSp, v.spine, w.spine)
        }
        v is VNe && w is VNe && v.head is HMeta && w.head is HMeta && v.head.meta == w.head.meta -> {
            val rSp = r.meld(top.rigidity(v.head.meta)).meld(top.rigidity(w.head.meta))
            unifySp(lvl, unfold, names, rSp, v.spine, w.spine)
        }
        v is VNe && w is VNe && v.head is HLocal && w.head is HLocal && v.head.ix == w.head.ix ->
            unifySp(lvl, unfold, names, r, v.spine, w.spine)

        v is VNe && v.head is HMeta && top[v.head.meta] is MetaUnsolved && r == Rigidity.Rigid ->
            solve(lvl, names, v.head.meta, v.spine, w)
        v is VNe && v.head is HMeta && top[v.head.meta] is MetaSolved ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, v.appSpine(top, v.spine), w)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")

        w is VNe && w.head is HMeta && top[w.head.meta] is MetaUnsolved && r == Rigidity.Rigid ->
            solve(lvl, names, w.head.meta, w.spine, v)
        w is VNe && w.head is HMeta && top[w.head.meta] is MetaSolved ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, w.appSpine(top, w.spine), v)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")

        v is VNe && v.head is HTop && top[v.head.lvl].defn != null ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, v.appSpine(top, v.spine), w)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")
        w is VNe && w.head is HTop && top[w.head.lvl].defn != null ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, w.appSpine(top, w.spine), v)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")

        else -> throw FlexRigidError(r, "failed to unify")
    }
}

fun VSpine.check(top: TopContext): Pair<Lvl, Renaming> {
    val s = it.size
    val x = mutableListOf<Pair<Lvl, Lvl>>()
    for ((i, v) in it.reversed().withIndex()) {
        val vsp = v.second.value.force(top)
        if (vsp !is VNe || vsp.head !is HLocal) throw FlexRigidError(Rigidity.Flex,"not local")
        x.add(Lvl(vsp.head.ix.it) to Lvl(s - i - 1))
    }
    return Lvl(it.size) to Renaming(x.toTypedArray())
}
fun GSpine.check(top: TopContext): Pair<Lvl, Renaming> {
    val s = it.size
    val x = mutableListOf<Pair<Lvl, Lvl>>()
    for ((i, v) in it.reversed().withIndex()) {
        val vsp = v.second.force(top)
        if (vsp !is GNe || vsp.head !is HLocal) throw UnifyError("glued spine")
        x.add(Lvl(vsp.head.ix.it) to Lvl(s - i - 1))
    }
    return Lvl(it.size) to Renaming(x.toTypedArray())
}

fun LocalContext.solve(lvl: Lvl, names: Array<String>, occurs: Meta, vsp: VSpine, v: Val) {
    val (lvlRen, vNew) = contract(vsp.check(top), v)
    top[occurs] = vNew.quoteSolution(top, lvl, names, occurs, lvlRen, null)
}

data class GluedSolutionState(val top: TopContext, val occurs: Meta, val shift: Int, val topLvl: Lvl)
fun Glued.checkSolution(top: TopContext, occurs: Meta, topLvl: Lvl, renLvl: Pair<Lvl, Renaming>): Boolean {
    val (renl, ren) = renLvl
    return checkSolution(GluedSolutionState(top, occurs, topLvl.it - renl.it, topLvl), topLvl, ren)
}

fun Glued.checkSolution(state: GluedSolutionState, lvl: Lvl, ren: Renaming): Boolean {
    val g = force(state.top)
    return when {
        g is GLam -> g.cl.inst(state.top, gvLocal(Ix(lvl.it))).checkSolution(state, lvl + 1, ren + (lvl to lvl - state.shift))
        g is GNe && g.head is HMeta && g.head.meta == state.occurs && g.gspine.strip(state.topLvl, lvl) -> true
        else -> {
            g.scopeCheck(state, lvl, ren)
            false
        }
    }
}
fun GSpine.strip(topLvl: Lvl, lvl: Lvl): Boolean {
    //TODO: maybe in reverse?
    var l = lvl.it
    for ((_, v) in it.reversed()) {
        if (v !is GNe || v.head !is HLocal || v.head.ix.it != l - 1) return false
        l--
    }
    return l == topLvl.it
}
fun Glued.scopeCheck(state: GluedSolutionState, lvl: Lvl, ren: Renaming) {
    val local = gvLocal(Ix(lvl.it))
    when (val g = force(state.top)) {
        is GU -> {}
        is GIrrelevant -> {}
        is GFun -> { g.a.g.scopeCheck(state, lvl, ren); g.b.g.scopeCheck(state, lvl, ren) }
        is GPi -> { g.ty.g.scopeCheck(state, lvl, ren); g.cl.inst(state.top, local).scopeCheck(state, lvl + 1, ren + (lvl to lvl - state.shift)) }
        is GLam -> g.cl.inst(state.top, local).scopeCheck(state, lvl + 1, ren + (lvl to lvl - state.shift))
        is GNe -> {
            when (g.head) {
                is HTop -> {}
                is HMeta -> if (g.head.meta == state.occurs) throw UnifyError("SEOccurs")
                is HLocal -> if (ren.apply(Lvl(g.head.ix.it)) == -1) throw UnifyError("SEScope")
            }
            for ((_, gSp) in g.gspine.it) {
                gSp.scopeCheck(state, lvl, ren)
            }
        }
    }
}

@Throws(ElabError::class)
fun LocalContext.gvUnify(a: GluedVal, b: GluedVal) {
    try {
        this.unify(a.v.value, b.v.value)
    } catch (fr: FlexRigidError) {
        when (fr.rigidity) {
            Rigidity.Rigid -> throw fr
            Rigidity.Flex -> gvUnify(lvl, names, a, b)
        }
    }
}

fun LocalContext.gvUnify(lvl: Lvl, names: Array<String>, a: GluedVal, b: GluedVal) {
    val v = a.g.force(top)
    val w = b.g.force(top)
    val local = gvLocal(Ix(lvl.it))
    when {
        v is GU && w is GU -> {}

        v is GNe && w is GNe && v.head is HMeta && w.head is HMeta -> when {
            v.head.meta < w.head.meta -> solve(lvl, names, w.head.meta, w.spine, w.gspine, GluedVal(a.v, v))
            v.head.meta > w.head.meta -> solve(lvl, names, v.head.meta, v.spine, v.gspine, GluedVal(b.v, w))
            else -> gvUnifySp(lvl, names, v.gspine, w.gspine, v.spine, w.spine)
        }

        v is GNe && v.head is HMeta -> solve(lvl, names, v.head.meta, v.spine, v.gspine, GluedVal(b.v, w))
        w is GNe && w.head is HMeta -> solve(lvl, names, w.head.meta, w.spine, w.gspine, GluedVal(a.v, v))

        v is GLam && w is GLam -> gvUnify(lvl + 1, names + v.n, v.cl.gvInst(top, local), w.cl.gvInst(top, local))
        v is GLam -> gvUnify(lvl + 1, names + v.n, v.cl.gvInst(top, local), w.gvApp(top, v.icit, local))
        w is GLam -> gvUnify(lvl + 1, names + w.n, w.cl.gvInst(top, local), v.gvApp(top, w.icit, local))

        v is GPi && w is GPi && v.n == w.n -> {
            gvUnify(lvl, names, v.ty, w.ty)
            gvUnify(lvl + 1, names + v.n, v.cl.gvInst(top, local), w.cl.gvInst(top, local))
        }
        v is GPi && w is GFun -> {
            gvUnify(lvl, names, v.ty, w.a)
            gvUnify(lvl + 1, names + v.n, v.cl.gvInst(top, local), w.b)
        }
        w is GPi && v is GFun -> {
            gvUnify(lvl, names, w.ty, v.a)
            gvUnify(lvl + 1, names + w.n, w.cl.gvInst(top, local), v.b)
        }

        v is GFun && w is GFun -> {
            gvUnify(lvl, names, v.a, w.a)
            gvUnify(lvl, names, v.b, w.b)
        }

        v is GNe && w is GNe && v.head is HTop && w.head is HTop && v.head.lvl == w.head.lvl ->
            gvUnifySp(lvl, names, v.gspine, w.gspine, v.spine, w.spine)
        v is GNe && w is GNe && v.head is HLocal && w.head is HLocal && v.head.ix == w.head.ix ->
            gvUnifySp(lvl, names, v.gspine, w.gspine, v.spine, w.spine)

        else -> throw UnifyError("failed to unify in glued mode")
    }
}

fun LocalContext.gvUnifySp(lvl: Lvl, names: Array<String>, ga: GSpine, gb: GSpine, va: VSpine, vb: VSpine) {
    if (setOf(ga.it.size, gb.it.size, va.it.size, vb.it.size).size != 1) throw UnifyError("spines differ")
    for (i in ga.it.indices) {
        if (setOf(ga.it[i].first, gb.it[i].first, va.it[i].first, vb.it[i].first).size != 1) throw UnifyError("spines differ")
        gvUnify(lvl, names, GluedVal(va.it[i].second, ga.it[i].second), GluedVal(va.it[i].second, ga.it[i].second))
    }
}

fun LocalContext.solve(lvl: Lvl, names: Array<String>, occurs: Meta, vsp: VSpine, gsp: GSpine, v: GluedVal) {
    val (renC, vC) = contract(gsp.check(top), v.v.value)
    val errRef = ErrRef()
    val rhs = vC.quoteSolution(top, lvl, names, occurs, renC, errRef)
    val err = errRef.err
    when {
        err == null -> top[occurs] = rhs
        err.rigidity == Rigidity.Rigid -> throw err
        else -> if (!v.g.checkSolution(top, occurs, this.lvl, renC)) top[occurs] = rhs
    }
}

sealed class MetaInsertion
data class MIUntilName(val n: String) : MetaInsertion()
object MIYes : MetaInsertion()
object MINo : MetaInsertion()

fun LocalContext.gvEval(t: Term): GluedVal = t.gvEval(top, vVals, gVals)

fun LocalContext.newMeta(): Term {
    val i = top.metas.size - 1
    val meta = Meta(i, top.metas[i].size)
    top[meta] = MetaUnsolved(top.currentPos)
    var tm: Term = TMeta(meta)
    for (x in boundIndices) {
        tm = TApp(Icit.Expl, tm, TLocal(Ix(lvl.it - x - 1)))
    }
    return tm
}

fun LocalContext.check(t: PreTerm, want: GluedVal): Term {
    top(t.loc)
    val g = want.g.force(top)
    val local = gvLocal(Ix(lvl.it))
    return when {
        t is RLet -> {
            val a = check(t.type, GVU)
            val gva = gvEval(a)
            val tm = check(t.defn, gva)
            val gvt = gvEval(tm)
            val u = localDefine(t.loc, t.n, gva, gvt).check(t.body, want)
            TLet(t.n, a, tm, u)
        }
        t is RLam && g is GPi && t.ni.match(g) -> TLam(t.n, g.icit, localBindSrc(t.loc, t.n, g.ty).check(t.body, g.cl.gvInst(top, local)))
        t is RLam && g is GFun && t.ni is NIExpl -> {
            TLam(t.n, Icit.Expl, localBindSrc(t.loc, t.n, g.a).check(t.body, g.b))
        }
        g is GPi && g.icit == Icit.Impl -> TLam(g.n, Icit.Impl, localBindIns(t.loc, g.n, g.ty).check(t, g.cl.gvInst(top, local)))
        t is RHole -> newMeta()
        else -> {
            val (tt, gvHas) = infer(MIYes, t)
            try {
                gvUnify(gvHas, GluedVal(want.v, want.g))
            } catch (e: ElabError) {
                throw ElabError(top.currentPos, this, "Failed to unify ${gvHas.g} and ${want.g}")
            }
            tt
        }
    }
}
fun NameOrIcit.match(g: GPi): Boolean = when (this) {
    NIImpl -> g.icit == Icit.Impl
    NIExpl -> g.icit == Icit.Expl
    is NIName -> g.n == n
}

fun LocalContext.insertMetas(mi: MetaInsertion, c: Pair<Term, GluedVal>): Pair<Term, GluedVal> {
    var (t, gva) = c
    when (mi) {
        MINo -> {}
        MIYes -> {
            var g = gva.g.force(top)
            while (g is GPi && g.icit == Icit.Impl) {
                val m = newMeta()
                t = TApp(Icit.Impl, t, m)
                gva = g.cl.gvInst(top, gvEval(m))
                g = gva.g.force(top)
            }
        }
        is MIUntilName -> {
            var g = gva.g.force(top)
            while (g is GPi && g.icit == Icit.Impl) {
                if (g.n == mi.n) {
                    return t to gva
                }
                val m = newMeta()
                t = TApp(Icit.Impl, t, m)
                gva = g.cl.gvInst(top, gvEval(m))
                g = gva.g.force(top)
            }
            throw ElabError(top.currentPos, this, "No named arg ${mi.n}")
        }
    }
    return t to gva
}

fun LocalContext.inferVar(n: String): Pair<Term, GluedVal> {
    val v = nameTable.it.getOrElse(n) { throw ElabError(null, this, "Variable $n out of scope") }
    for (ni in v) {
        when {
            ni is NITop -> return TTop(ni.lvl) to top.topEntries[ni.lvl.it].type.gv
            ni is NILocal && !ni.inserted -> {
                val ix = lvl.it - ni.lvl.it - 1
                return TLocal(Ix(ix)) to types[types.size - ix - 1]
            }
        }
    }
    throw ElabError(null, this, "Variable $n out of scope")
}

fun LocalContext.infer(mi: MetaInsertion, r: PreTerm): Pair<Term, GluedVal> {
    top(r.loc)
    return when (r) {
        is RU -> TU to GVU
        is RNat -> inferVar("Nat")
        is RVar -> insertMetas(mi, inferVar(r.n))
        is RStopMeta -> infer(MINo, r.body)
        is RForeign -> {
            val a = check(r.type, GVU)
            TForeign(r.lang, r.eval) to gvEval(a)
        }
        is RHole -> {
            val m1 = newMeta()
            val m2 = newMeta()
            m1 to gvEval(m2)
        }
        is RPi -> {
            val a = check(r.type, GVU)
            val b = localBindSrc(r.loc, r.n, gvEval(a)).check(r.body, GVU)
            TPi(r.n, r.icit, a, b) to GVU
        }
        is RFun -> TFun(check(r.l, GVU), check(r.r, GVU)) to GVU
        is RLet -> {
            val a = check(r.type, GVU)
            val gva = gvEval(a)
            val t = check(r.defn, gva)
            val gvt = gvEval(t)
            val (u, gvb) = localDefine(r.loc, r.n, gvt, gva).infer(mi, r.body)
            TLet(r.n, a, t, u) to gvb
        }
//  -- TODO: do the case where a meta is inferred for "t"
        is RApp -> {
            val ins = when (r.ni) {
                is NIName -> MIUntilName(r.ni.n)
                NIImpl -> MINo
                NIExpl -> MIYes
            }
            val (t, gva) = infer(ins, r.rator)
            insertMetas(mi, when (val g = gva.g.force(top)) {
                is GPi -> {
                    when {
                        r.ni is NIExpl && g.icit != Icit.Expl -> { top(r.loc); throw ElabError(r.loc, this, "AppIcit") }
                        r.ni is NIImpl && g.icit != Icit.Impl -> { top(r.loc); throw ElabError(r.loc, this, "AppIcit") }
                    }
                    val u = check(r.rand, g.ty)
                    TApp(g.icit, t, u) to g.cl.gvInst(top, gvEval(u))
                }
                is GFun -> {
                    if (r.ni !is NIExpl) throw ElabError(r.loc, this, "Icit mismatch")
                    val u = check(r.rand, g.a)
                    TApp(Icit.Expl, t, u) to g.b
                }
                else -> throw ElabError(r.loc, this, "function expected, instead got $g")
            })
        }
        is RLam -> {
            val icit = when (r.ni) {
                NIImpl -> Icit.Impl
                NIExpl -> Icit.Expl
                is NIName -> throw ElabError(r.loc, this, "named lambda")
            }
            val gva = gvEval(newMeta())
            val (t, gvb) = localBindSrc(r.loc, r.n, gva).infer(MIYes, r.body)
            val b = gvb.v.value.quote(top, lvl + 1)
            insertMetas(mi, TLam(r.n, icit, t) to GluedVal(
                lazyOf(VPi(r.n, icit, gva.v, VCl(vVals, b))),
                GPi(r.n, icit, gva, GCl(gVals, vVals, b))
            ))
        }
    }
}

fun checkProgram(top: TopContext, p: Program): NameTable {
    val ntbl = NameTable()
    for (e in p) {
        top.metas.add(mutableListOf())
        val ctx = LocalContext(top, ntbl)
        when (e) {
            is RElab -> TODO("elab")
            is RNorm -> TODO("norm")
            is RDecl -> {
                top(e.loc)
                var a = ctx.check(e.ty, GVU)
                ctx.simplifyMetaBlock(top)
                a = a.inline(top, Lvl(0), emptyVEnv)
                ntbl.addName(e.n, NITop(e.loc, Lvl(top.topEntries.size)))
                top.topEntries.add(TopEntry(e.loc, e.n, null, GluedTerm(a, ctx.gvEval(a))))
            }
            is RDefn -> {
                top(e.loc)
                val ty = e.ty ?: TODO("pull from TopEntries, Postulate")
                var a = ctx.check(ty, GVU)
                var gva = ctx.gvEval(a)
                var t = ctx.check(e.tm, gva)
                ctx.simplifyMetaBlock(top)
                a = a.inline(top, Lvl(0), emptyVEnv)
                for ((ix, x) in top.metas.withIndex()) {
                    println("Block $ix")
                    for (meta in x) println(x)
                }
                println(t)
                t = t.inline(top, Lvl(0), emptyVEnv)
                gva = ctx.gvEval(a)
                val gvt = ctx.gvEval(t)
                ntbl.addName(e.n, NITop(e.loc, Lvl(top.topEntries.size)))
                top.topEntries.add(TopEntry(e.loc, e.n, GluedTerm(t, gvt), GluedTerm(a, gva)))
            }
        }
    }
    return ntbl
}

fun TopContext.show(ntbl: NameTable, tm: Term): String {
    TODO("show0")
}

fun nfMain(s: String) {
    val top = TopContext()
    val ntbl = checkProgram(top, parseModule(s))
    for ((i, topMeta) in top.metas.zip(top.topEntries).withIndex()) {
        val (metaBlock, topEntry) = topMeta
        for ((j, meta) in metaBlock.withIndex()) {
            if (meta !is MetaSolved) throw UnifyError("Unsolved metablock")
            if (meta.unfoldable) continue
            println("  $i.$j = ${top.show(ntbl, meta.tm)}")
        }
        when (topEntry.defn) {
            null -> println("${topEntry.name} : ${top.show(ntbl, topEntry.type.tm)}")
            else -> {
                println("${topEntry.name} : ${top.show(ntbl, topEntry.type.tm)}")
                println("${topEntry.name} = ${top.show(ntbl, topEntry.defn.tm)}")
            }
        }
    }
}

fun main() = nfMain("""
    id : {A} → A → A = λ x. x.
    idTest : {A} → A → A = id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id id.
""".trimMargin())

// fun main() = nfMain("Nat : * = (n : *) → (n → n) → n → n")
