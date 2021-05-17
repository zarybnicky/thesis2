package montuno.interpreter

import montuno.syntax.*
import kotlin.Throws

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
data class ScopeCheckState(val occurs: Meta, val errRef: ErrRef?, val shift: Int)
@Throws(FlexRigidError::class)
fun Val.quoteSolution(topLvl: Lvl, names: Array<String>, occurs: Meta, renL: Pair<Lvl, Renaming>, errRef: ErrRef?): Term {
    val (renl, ren) = renL
    return scopeCheck(ScopeCheckState(occurs, errRef, topLvl.it - renl.it), topLvl, ren).lams(topLvl, names, ren)
}
fun Val.scopeCheck(state: ScopeCheckState, lvl: Lvl, ren: Renaming): Term = when (val v = force()) {
    VU -> TU
    VIrrelevant -> TIrrelevant
    is VNat -> TNat(v.n)
    is VFun -> TFun(v.a.value.scopeCheck(state, lvl, ren), v.b.value.scopeCheck(state, lvl, ren))
    is VLam -> TLam(v.n, v.icit, v.cl.inst(lazyOf(vLocal(Ix(lvl.it)))).scopeCheck(state,lvl + 1, ren + (lvl to lvl - state.shift)))
    is VPi -> TPi(v.n, v.icit, v.ty.value.scopeCheck(state, lvl, ren),
        v.cl.inst(lazyOf(vLocal(Ix(lvl.it)))).scopeCheck(state, lvl + 1, ren + (lvl to lvl - state.shift)))
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
                MontunoPure.top[v.head.meta] is MetaSolved ->
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
    val v = a.force()
    val w = b.force()
    val local = lazyOf(vLocal(Ix(lvl.it)))
    when {
        v is VIrrelevant || w is VIrrelevant -> {}
        v is VU && w is VU -> {}
        v is VFun && w is VFun -> {
            unify(lvl, unfold, names, r, v.a.value, w.a.value)
            unify(lvl, unfold, names, r, v.b.value, w.b.value)
        }

        v is VLam && w is VLam -> unify(lvl + 1, unfold, names + v.n, r, v.cl.inst(local), w.cl.inst(local))
        v is VLam -> unify(lvl + 1, unfold, names + v.n, r, v.cl.inst(local), w.app(v.icit, local))
        w is VLam -> unify(lvl + 1, unfold, names + w.n, r, w.cl.inst(local), v.app(w.icit, local))

        v is VPi && w is VPi && v.icit == w.icit -> {
            unify(lvl, unfold, names, r, v.ty.value, w.ty.value)
            unify(lvl + 1, unfold, names, r, v.cl.inst(local), w.cl.inst(local))
        }
        v is VPi && w is VFun -> {
            unify(lvl, unfold, names, r, v.ty.value, w.a.value)
            unify(lvl + 1, unfold, names + v.n, r, v.cl.inst(local), w.b.value)
        }
        w is VPi && v is VFun -> {
            unify(lvl, unfold, names, r, w.ty.value, v.a.value)
            unify(lvl + 1, unfold, names + w.n, r, w.cl.inst(local), v.b.value)
        }

        v is VNe && w is VNe && v.head is HTop && w.head is HTop && v.head.lvl == w.head.lvl -> {
            val rSp = r.meld(MontunoPure.top.rigidity(v.head.lvl)).meld(MontunoPure.top.rigidity(w.head.lvl))
            unifySp(lvl, unfold, names, rSp, v.spine, w.spine)
        }
        v is VNe && w is VNe && v.head is HMeta && w.head is HMeta && v.head.meta == w.head.meta -> {
            val rSp = r.meld(MontunoPure.top.rigidity(v.head.meta)).meld(MontunoPure.top.rigidity(w.head.meta))
            unifySp(lvl, unfold, names, rSp, v.spine, w.spine)
        }
        v is VNe && w is VNe && v.head is HLocal && w.head is HLocal && v.head.ix == w.head.ix ->
            unifySp(lvl, unfold, names, r, v.spine, w.spine)

        v is VNe && v.head is HMeta && MontunoPure.top[v.head.meta] is MetaUnsolved && r == Rigidity.Rigid ->
            solve(lvl, names, v.head.meta, v.spine, w)
        v is VNe && v.head is HMeta && MontunoPure.top[v.head.meta] is MetaSolved ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, v.appSpine(v.spine), w)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")

        w is VNe && w.head is HMeta && MontunoPure.top[w.head.meta] is MetaUnsolved && r == Rigidity.Rigid ->
            solve(lvl, names, w.head.meta, w.spine, v)
        w is VNe && w.head is HMeta && MontunoPure.top[w.head.meta] is MetaSolved ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, w.appSpine(w.spine), v)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")

        v is VNe && v.head is HTop && MontunoPure.top[v.head.lvl].defn != null ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, v.appSpine(v.spine), w)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")
        w is VNe && w.head is HTop && MontunoPure.top[w.head.lvl].defn != null ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, w.appSpine(w.spine), v)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")

        else -> throw FlexRigidError(r, "failed to unify:\n$v\n$w")
    }
}

fun VSpine.check(): Pair<Lvl, Renaming> {
    val s = it.size
    val x = mutableListOf<Pair<Lvl, Lvl>>()
    for ((i, v) in it.reversed().withIndex()) {
        val vsp = v.second.value.force()
        if (vsp !is VNe || vsp.head !is HLocal) throw FlexRigidError(Rigidity.Flex,"not local")
        x.add(Lvl(vsp.head.ix.it) to Lvl(s - i - 1))
    }
    return Lvl(it.size) to Renaming(x.toTypedArray())
}
fun GSpine.check(): Pair<Lvl, Renaming> {
    val s = it.size
    val x = mutableListOf<Pair<Lvl, Lvl>>()
    for ((i, v) in it.reversed().withIndex()) {
        val vsp = v.second.force()
        if (vsp !is GNe || vsp.head !is HLocal) throw UnifyError("glued spine")
        x.add(Lvl(vsp.head.ix.it) to Lvl(s - i - 1))
    }
    return Lvl(it.size) to Renaming(x.toTypedArray())
}

fun solve(lvl: Lvl, names: Array<String>, occurs: Meta, vsp: VSpine, v: Val) {
    val (lvlRen, vNew) = contract(vsp.check(), v)
    MontunoPure.top[occurs] = vNew.quoteSolution(lvl, names, occurs, lvlRen, null)
}

data class GluedSolutionState(val occurs: Meta, val shift: Int, val topLvl: Lvl)
fun Glued.checkSolution(occurs: Meta, topLvl: Lvl, renLvl: Pair<Lvl, Renaming>): Boolean {
    val (renl, ren) = renLvl
    return checkSolution(GluedSolutionState(occurs, topLvl.it - renl.it, topLvl), topLvl, ren)
}

fun Glued.checkSolution(state: GluedSolutionState, lvl: Lvl, ren: Renaming): Boolean {
    val g = force()
    return when {
        g is GLam -> g.cl.inst(gvLocal(Ix(lvl.it))).checkSolution(state, lvl + 1, ren + (lvl to lvl - state.shift))
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
    when (val g = force()) {
        is GU -> {}
        is GIrrelevant -> {}
        is GFun -> { g.a.g.scopeCheck(state, lvl, ren); g.b.g.scopeCheck(state, lvl, ren) }
        is GPi -> { g.ty.g.scopeCheck(state, lvl, ren); g.cl.inst(local).scopeCheck(state, lvl + 1, ren + (lvl to lvl - state.shift)) }
        is GLam -> g.cl.inst(local).scopeCheck(state, lvl + 1, ren + (lvl to lvl - state.shift))
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
    val v = a.g.force()
    val w = b.g.force()
    val local = gvLocal(Ix(lvl.it))
    when {
        v is GU && w is GU -> {}

        v is GNe && w is GNe && v.head is HMeta && w.head is HMeta -> when {
            v.head.meta < w.head.meta -> solve(lvl, names, w.head.meta, w.gspine, GluedVal(a.v, v))
            v.head.meta > w.head.meta -> solve(lvl, names, v.head.meta, v.gspine, GluedVal(b.v, w))
            else -> gvUnifySp(lvl, names, v.gspine, w.gspine, v.spine, w.spine)
        }

        v is GNe && v.head is HMeta -> solve(lvl, names, v.head.meta, v.gspine, GluedVal(b.v, w))
        w is GNe && w.head is HMeta -> solve(lvl, names, w.head.meta, w.gspine, GluedVal(a.v, v))

        v is GLam && w is GLam -> gvUnify(lvl + 1, names + v.n, v.cl.gvInst(local), w.cl.gvInst(local))
        v is GLam -> gvUnify(lvl + 1, names + v.n, v.cl.gvInst(local), w.gvApp(v.icit, local))
        w is GLam -> gvUnify(lvl + 1, names + w.n, w.cl.gvInst(local), v.gvApp(w.icit, local))

        v is GPi && w is GPi && v.n == w.n -> {
            gvUnify(lvl, names, v.ty, w.ty)
            gvUnify(lvl + 1, names + v.n, v.cl.gvInst(local), w.cl.gvInst(local))
        }
        v is GPi && w is GFun -> {
            gvUnify(lvl, names, v.ty, w.a)
            gvUnify(lvl + 1, names + v.n, v.cl.gvInst(local), w.b)
        }
        w is GPi && v is GFun -> {
            gvUnify(lvl, names, w.ty, v.a)
            gvUnify(lvl + 1, names + w.n, w.cl.gvInst(local), v.b)
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

fun LocalContext.solve(lvl: Lvl, names: Array<String>, occurs: Meta, gsp: GSpine, v: GluedVal) {
    val (renC, vC) = contract(gsp.check(), v.v.value)
    val errRef = ErrRef()
    val rhs = vC.quoteSolution(lvl, names, occurs, renC, errRef)
    val err = errRef.err
    when {
        err == null -> MontunoPure.top[occurs] = rhs
        err.rigidity == Rigidity.Rigid -> throw err
        else -> if (!v.g.checkSolution(occurs, this.lvl, renC)) MontunoPure.top[occurs] = rhs
    }
}

sealed class MetaInsertion {
    data class UntilName(val n: String) : MetaInsertion()
    object Yes : MetaInsertion()
    object No : MetaInsertion()
}

fun LocalContext.gvEval(t: Term): GluedVal = t.gvEval(vVals, gVals)

fun LocalContext.newMeta(): Term {
    val i = MontunoPure.top.metas.size - 1
    val meta = Meta(i, MontunoPure.top.metas[i].size)
    MontunoPure.top[meta] = MetaUnsolved(MontunoPure.top.loc)
    var tm: Term = TMeta(meta)
    for (x in boundIndices) {
        tm = TApp(Icit.Expl, tm, TLocal(Ix(lvl.it - x - 1)))
    }
    return tm
}

fun LocalContext.check(t: PreTerm, want: GluedVal): Term {
    MontunoPure.top.loc = t.loc
    val g = want.g.force()
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
        t is RLam && g is GPi && t.ni.match(g) -> TLam(t.n, g.icit, localBindSrc(t.loc, t.n, g.ty).check(t.body, g.cl.gvInst(local)))
        t is RLam && g is GFun && t.ni is NIExpl -> TLam(t.n, Icit.Expl, localBindSrc(t.loc, t.n, g.a).check(t.body, g.b))
        g is GPi && g.icit == Icit.Impl -> TLam(g.n, Icit.Impl, localBindIns(t.loc, g.n, g.ty).check(t, g.cl.gvInst(local)))
        t is RHole -> newMeta()
        else -> {
            val (tt, gvHas) = infer(MetaInsertion.Yes, t)
            try {
                gvUnify(gvHas, GluedVal(want.v, want.g))
            } catch (e: ElabError) {
                throw ElabError(MontunoPure.top.loc, this, "Failed to unify ${gvHas.g} and ${want.g}")
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
        MetaInsertion.No -> {}
        MetaInsertion.Yes -> {
            var g = gva.g.force()
            while (g is GPi && g.icit == Icit.Impl) {
                val m = newMeta()
                t = TApp(Icit.Impl, t, m)
                gva = g.cl.gvInst(gvEval(m))
                g = gva.g.force()
            }
        }
        is MetaInsertion.UntilName -> {
            var g = gva.g.force()
            while (g is GPi && g.icit == Icit.Impl) {
                if (g.n == mi.n) {
                    return t to gva
                }
                val m = newMeta()
                t = TApp(Icit.Impl, t, m)
                gva = g.cl.gvInst(gvEval(m))
                g = gva.g.force()
            }
            throw ElabError(MontunoPure.top.loc, this, "No named arg ${mi.n}")
        }
    }
    return t to gva
}

fun LocalContext.inferVar(n: String): Pair<Term, GluedVal> {
    for (ni in nameTable[n].asReversed()) {
        when {
            ni is NITop -> return TTop(ni.lvl) to MontunoPure.top.topEntries[ni.lvl.it].type.gv
            ni is NILocal && !ni.inserted -> {
                val ix = lvl.it - ni.lvl.it - 1
                println("${types.size}/${lvl.it} - ${ni.lvl.it} - 1 = $ix from ${types.joinToString(", ")}")
                return TLocal(Ix(ix)) to types[ni.lvl.it]
            }
        }
    }
    throw ElabError(null, this, "Variable $n out of scope")
}

fun LocalContext.infer(mi: MetaInsertion, r: PreTerm): Pair<Term, GluedVal> {
    MontunoPure.top.loc = r.loc
    return when (r) {
        is RU -> TU to GVU
        is RNat -> TNat(r.n) to inferVar("Nat").first.gvEval(emptyVEnv, emptyGEnv)
        is RVar -> insertMetas(mi, inferVar(r.n))
        is RStopMeta -> infer(MetaInsertion.No, r.body)
        is RForeign -> {
            val a = check(r.type, GVU)
            TForeign(r.lang, r.eval, a) to gvEval(a)
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
                is NIName -> MetaInsertion.UntilName(r.ni.n)
                NIImpl -> MetaInsertion.No
                NIExpl -> MetaInsertion.Yes
            }
            val (t, gva) = infer(ins, r.rator)
            insertMetas(mi, when (val g = gva.g.force()) {
                is GPi -> {
                    when {
                        r.ni is NIExpl && g.icit != Icit.Expl -> { MontunoPure.top.loc = r.loc; throw ElabError(r.loc, this, "AppIcit") }
                        r.ni is NIImpl && g.icit != Icit.Impl -> { MontunoPure.top.loc = r.loc; throw ElabError(r.loc, this, "AppIcit") }
                    }
                    val u = check(r.rand, g.ty)
                    TApp(g.icit, t, u) to g.cl.gvInst(gvEval(u))
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
            val (t, gvb) = localBindSrc(r.loc, r.n, gva).infer(MetaInsertion.Yes, r.body)
            val b = gvb.v.value.quote(lvl + 1)
            insertMetas(mi, TLam(r.n, icit, t) to GluedVal(
                lazyOf(VPi(r.n, icit, gva.v, VCl(vVals, b))),
                GPi(r.n, icit, gva, GCl(gVals, vVals, b))
            ))
        }
    }
}

fun checkTopLevel(e: TopLevel): String? {
    MontunoPure.top.metas.add(mutableListOf())
    val ctx = LocalContext(MontunoPure.top.ntbl)
    MontunoPure.top.loc = e.loc
    return when (e) {
        is RTerm -> when (e.cmd) {
            Pragma.ParseOnly -> e.tm.toString()
            Pragma.Reset -> { MontunoPure.top.reset(); null }
            Pragma.Elaborated -> { MontunoPure.top.printElaborated(); null }
            Pragma.Nothing -> ctx.infer(MetaInsertion.No, e.tm!!).first.pretty().toString()
            Pragma.Type -> {
                val (tm, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ty.g.quote(Lvl(0)).pretty().toString()
            }
            Pragma.NormalType -> {
                val (tm, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ty.v.value.quote(Lvl(0), true).pretty().toString()
            }
            Pragma.Elaborate -> {
                val (tm, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.gvEval(tm).g.quote((Lvl(0))).pretty().toString()
            }
            Pragma.Normalize -> {
                val (tm, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.gvEval(tm).v.value.quote(Lvl(0), true).pretty().toString()
            }
        }
        is RDecl -> {
            var a = ctx.check(e.ty, GVU)
            ctx.simplifyMetaBlock()
            a = a.inline(Lvl(0), emptyVEnv)
            MontunoPure.top.addTopLevel(e.n, e.loc, null, a)
            return null
        }
        is RDefn -> {
            var a: Term = if (e.ty != null) {
                ctx.check(e.ty, GVU)
            } else try {
                ctx.inferVar(e.n).second.g.quote(Lvl(0))
            } catch (e: ElabError) {
                TU
            }
            var t = ctx.check(e.tm, ctx.gvEval(a))
            ctx.simplifyMetaBlock()
            a = a.inline(Lvl(0), emptyVEnv)
            t = t.inline(Lvl(0), emptyVEnv)
            MontunoPure.top.addTopLevel(e.n, e.loc, t, a)
            return null
        }
    }
}
