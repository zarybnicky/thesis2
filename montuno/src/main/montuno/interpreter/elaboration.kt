package montuno.interpreter

import montuno.*
import montuno.common.*
import montuno.syntax.*
import kotlin.Throws

fun contract(lvlRen: Pair<Lvl, Renaming>, v: Val): Pair<Pair<Lvl, Renaming>, Val> {
    val (lvl, ren) = lvlRen
    // TODO: check whether this works at all???
    if (v !is VNe || v.spine.it.size <= ren.it.size) return (lvl to ren) to v
    val mid = ren.it.size - v.spine.it.size
    val renOverlap = ren.it.copyOfRange(mid, ren.it.size).map { it.first.it }.toTypedArray()
    val spineLocal = v.spine.it
        .map { it.first to it.second }
        .map { (_, vSp) -> if (vSp is VNe && vSp.head is HLocal) vSp.head.lvl.it else -1 }.toTypedArray()
    if (renOverlap contentEquals spineLocal) {
        return (lvl - renOverlap.size to Renaming(ren.it.copyOfRange(0, mid))) to VNe(v.head, VSpine())
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
fun Val.scopeCheck(state: ScopeCheckState, lvl: Lvl, ren: Renaming): Term = when (val v = force(false)) {
    VUnit -> TU
    VIrrelevant -> TIrrelevant
    is VNat -> TNat(v.n)
    is VFun -> TFun(v.a.scopeCheck(state, lvl, ren), v.b.scopeCheck(state, lvl, ren))
    is VLam -> TLam(v.n, v.icit, v.cl.inst(vLocal(lvl)).scopeCheck(state,lvl + 1, ren + (lvl to lvl - state.shift)))
    is VPi -> TPi(v.n, v.icit, v.ty.scopeCheck(state, lvl, ren),
        v.cl.inst(vLocal(lvl)).scopeCheck(state, lvl + 1, ren + (lvl to lvl - state.shift)))
    is VNe -> {
        var root = when (v.head) {
            is HTop -> TTop(v.head.lvl)
            is HLocal -> {
                val x = ren.apply(v.head.lvl)
                if (x == null) {
                    val err = FlexRigidError(Rigidity.Flex)
                    if (state.errRef == null) throw err else state.errRef.err = err
                    TIrrelevant
                }
                else TLocal(x.toIx(lvl) - state.shift)
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
            root = TApp(icit, root, vSp.scopeCheck(state, lvl, ren))
        }
        root
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
    val local = vLocal(lvl)
    when {
        v is VIrrelevant || w is VIrrelevant -> {}
        v is VUnit && w is VUnit -> {}
        v is VFun && w is VFun -> {
            unify(lvl, unfold, names, r, v.a, w.a)
            unify(lvl, unfold, names, r, v.b, w.b)
        }

        v is VLam && w is VLam -> unify(lvl + 1, unfold, names + v.n, r, v.cl.inst(local), w.cl.inst(local))
        v is VLam -> unify(lvl + 1, unfold, names + v.n, r, v.cl.inst(local), w.app(v.icit, local))
        w is VLam -> unify(lvl + 1, unfold, names + w.n, r, w.cl.inst(local), v.app(w.icit, local))

        v is VPi && w is VPi && v.icit == w.icit -> {
            unify(lvl, unfold, names, r, v.ty, w.ty)
            unify(lvl + 1, unfold, names, r, v.cl.inst(local), w.cl.inst(local))
        }
        v is VPi && w is VFun -> {
            unify(lvl, unfold, names, r, v.ty, w.a)
            unify(lvl + 1, unfold, names + v.n, r, v.cl.inst(local), w.b)
        }
        w is VPi && v is VFun -> {
            unify(lvl, unfold, names, r, w.ty, v.a)
            unify(lvl + 1, unfold, names + w.n, r, w.cl.inst(local), v.b)
        }

        v is VNe && w is VNe && v.head is HLocal && w.head is HLocal && v.head.lvl == w.head.lvl ->
            unifySp(lvl, unfold, names, r, v.spine, w.spine)

        v is VNe && w is VNe && v.head is HMeta && w.head is HMeta && v.head.meta == w.head.meta -> {
            val rSp = r.meld(MontunoPure.top.rigidity(v.head.meta)).meld(MontunoPure.top.rigidity(w.head.meta))
            unifySp(lvl, unfold, names, rSp, v.spine, w.spine)
        }
        v is VNe && v.head is HMeta && MontunoPure.top[v.head.meta] is MetaUnsolved && r == Rigidity.Rigid ->
            solve(Rigidity.Flex, lvl, names, v.head.meta, v.spine, w)
        v is VNe && v.head is HMeta && MontunoPure.top[v.head.meta] is MetaSolved ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, v.appSpine(v.spine), w)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")
        w is VNe && w.head is HMeta && MontunoPure.top[w.head.meta] is MetaUnsolved && r == Rigidity.Rigid ->
            solve(Rigidity.Flex, lvl, names, w.head.meta, w.spine, v)
        w is VNe && w.head is HMeta && MontunoPure.top[w.head.meta] is MetaSolved ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, w.appSpine(w.spine), v)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")

        v is VNe && w is VNe && v.head is HTop && w.head is HTop && v.head.lvl == w.head.lvl -> {
            val rSp = r.meld(MontunoPure.top.rigidity(v.head.lvl)).meld(MontunoPure.top.rigidity(w.head.lvl))
            unifySp(lvl, unfold, names, rSp, v.spine, w.spine)
        }
        v is VNe && v.head is HTop && MontunoPure.top[v.head.lvl].defn != null ->
            if (unfold > 0) unify(lvl, unfold - 1, names, r, v.appSpine(v.spine), w)
            else throw FlexRigidError(Rigidity.Flex, "cannot unfold")
        w is VNe && w.head is HTop && MontunoPure.top[w.head.lvl].defn != null ->
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
        if (vsp !is VNe || vsp.head !is HLocal) throw FlexRigidError(mode,"not local")
        x.add(vsp.head.lvl to Lvl(s - i - 1))
    }
    return Lvl(it.size) to Renaming(x.toTypedArray())
}

fun Val.checkSolution(occurs: Meta, topLvl: Lvl, renLvl: Pair<Lvl, Renaming>): Boolean {
    val (renl, ren) = renLvl
    return checkSolution(occurs, topLvl.it - renl.it, topLvl, topLvl, ren)
}

fun Val.checkSolution(occurs: Meta, shift: Int, topLvl: Lvl, lvl: Lvl, ren: Renaming): Boolean {
    val v = force(false)
    return when {
        v is VLam -> v.cl.inst(vLocal(lvl)).checkSolution(occurs, shift, topLvl, lvl + 1, ren + (lvl to lvl - shift))
        v is VNe && v.head is HMeta && v.head.meta == occurs && v.spine.strip(topLvl, lvl) -> true
        else -> {
            v.scopeCheck(occurs, shift, topLvl, lvl, ren)
            false
        }
    }
}
fun VSpine.strip(topLvl: Lvl, lvl: Lvl): Boolean {
    //TODO: maybe in reverse?
    var l = lvl.it
    for ((_, v) in it.reversed()) {
        if (v !is VNe || v.head !is HLocal || v.head.lvl.it != l - 1) return false
        l--
    }
    return l == topLvl.it
}
fun Val.scopeCheck(occurs: Meta, shift: Int, topLvl: Lvl, lvl: Lvl, ren: Renaming) {
    val local = vLocal(lvl)
    when (val v = force(false)) {
        is VUnit -> {}
        is VIrrelevant -> {}
        is VFun -> { v.a.scopeCheck(occurs, shift, topLvl, lvl, ren); v.b.scopeCheck(occurs, shift, topLvl, lvl, ren) }
        is VPi -> { v.ty.scopeCheck(occurs, shift, topLvl, lvl, ren); v.cl.inst(local).scopeCheck(occurs, shift, topLvl, lvl + 1, ren + (lvl to lvl - shift)) }
        is VLam -> v.cl.inst(local).scopeCheck(occurs, shift, topLvl, lvl + 1, ren + (lvl to lvl - shift))
        is VNe -> {
            when (v.head) {
                is HTop -> {}
                is HMeta -> if (v.head.meta == occurs) throw UnifyError("SEOccurs")
                is HLocal -> if (ren.apply(v.head.lvl) == null) throw UnifyError("SEScope")
            }
            for ((_, gSp) in v.spine.it) {
                gSp.scopeCheck(occurs, shift, topLvl, lvl, ren)
            }
        }
    }
}

@Throws(ElabError::class)
fun LocalContext.unify(a: Val, b: Val) {
    try {
        unify(lvl, unfoldLimit, names, Rigidity.Rigid, a, b)
    } catch (fr: FlexRigidError) {
        when (fr.rigidity) {
            Rigidity.Rigid -> throw fr
            Rigidity.Flex -> gUnify(lvl, names, a, b)
        }
    }
}

fun LocalContext.gUnify(lvl: Lvl, names: List<String>, a: Val, b: Val) {
    val v = a.force(true)
    val w = b.force(true)
    val local = vLocal(lvl)
    when {
        v is VUnit && w is VUnit -> {}

        v is VNe && w is VNe && v.head is HMeta && w.head is HMeta -> when {
            v.head.meta < w.head.meta -> solve(Rigidity.Rigid, lvl, names, w.head.meta, w.spine, a)
            v.head.meta > w.head.meta -> solve(Rigidity.Rigid, lvl, names, v.head.meta, v.spine, b)
            else -> unifySp(lvl, names, v.spine, w.spine)
        }

        v is VNe && v.head is HMeta -> solve(Rigidity.Rigid, lvl, names, v.head.meta, v.spine, b)
        w is VNe && w.head is HMeta -> solve(Rigidity.Rigid, lvl, names, w.head.meta, w.spine, a)

        v is VLam && w is VLam -> gUnify(lvl + 1, names + v.n, v.cl.inst(local), w.cl.inst(local))
        v is VLam -> gUnify(lvl + 1, names + v.n, v.cl.inst(local), w.app(v.icit, local))
        w is VLam -> gUnify(lvl + 1, names + w.n, w.cl.inst(local), v.app(w.icit, local))

        v is VPi && w is VPi && v.n == w.n -> {
            gUnify(lvl, names, v.ty, w.ty)
            gUnify(lvl + 1, names + v.n, v.cl.inst(local), w.cl.inst(local))
        }
        v is VPi && w is VFun -> {
            gUnify(lvl, names, v.ty, w.a)
            gUnify(lvl + 1, names + v.n, v.cl.inst(local), w.b)
        }
        w is VPi && v is VFun -> {
            gUnify(lvl, names, w.ty, v.a)
            gUnify(lvl + 1, names + w.n, w.cl.inst(local), v.b)
        }

        v is VFun && w is VFun -> {
            gUnify(lvl, names, v.a, w.a)
            gUnify(lvl, names, v.b, w.b)
        }

        v is VNe && w is VNe && v.head is HTop && w.head is HTop && v.head.lvl == w.head.lvl ->
            unifySp(lvl, names, v.spine, w.spine)
        v is VNe && w is VNe && v.head is HLocal && w.head is HLocal && v.head.lvl == w.head.lvl ->
            unifySp(lvl, names, v.spine, w.spine)

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
        err == null -> MontunoPure.top[occurs] = rhs
        err.rigidity == Rigidity.Rigid -> throw err
        else -> if (!v.checkSolution(occurs, this.lvl, renC)) MontunoPure.top[occurs] = rhs
    }
}

fun LocalContext.eval(t: Term): Val = t.eval(vals)

fun LocalContext.newMeta(): Term {
    val i = MontunoPure.top.metas.size - 1
    val meta = Meta(i, MontunoPure.top.metas[i].size)
    MontunoPure.top[meta] = MetaUnsolved(MontunoPure.top.loc)
    var tm: Term = TMeta(meta)
    for (x in boundLevels) {
        tm = TApp(Icit.Expl, tm, TLocal(Lvl(x).toIx(lvl)))
    }
    return tm
}

fun LocalContext.check(t: PreTerm, want: Val): Term {
    MontunoPure.top.loc = t.loc
    val v = want.force(true)
    return when {
        t is RLet -> {
            val a = check(t.type, VUnit)
            val va = eval(a)
            val tm = check(t.defn, va)
            val vt = eval(tm)
            val u = localDefine(t.loc, t.n, va, vt).check(t.body, want)
            TLet(t.n, a, tm, u)
        }
        t is RLam && v is VPi && t.ni.match(v) -> TLam(t.n, v.icit, localBindSrc(t.loc, t.n, v.ty).check(t.body, v.cl.inst(vLocal(lvl))))
        t is RLam && v is VFun && t.ni is NIExpl -> TLam(t.n, Icit.Expl, localBindSrc(t.loc, t.n, v.a).check(t.body, v.b))
        v is VPi && v.icit == Icit.Impl -> TLam(v.n, Icit.Impl, localBindIns(t.loc, v.n, v.ty).check(t, v.cl.inst(vLocal(lvl))))
        t is RHole -> newMeta()
        else -> {
            val (tt, has) = infer(MetaInsertion.Yes, t)
            try {
                println("$has ?= $want")
                unify(has, want)
            } catch (e: ElabError) {
                throw ElabError(MontunoPure.top.loc, this, "Failed to unify $has and $want")
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

fun LocalContext.insertMetas(mi: MetaInsertion, c: Pair<Term, Val>): Pair<Term, Val> {
    var (t, va) = c
    when (mi) {
        MetaInsertion.No -> {}
        MetaInsertion.Yes -> {
            var vaf = va.force(false)
            while (vaf is VPi && vaf.icit == Icit.Impl) {
                val m = newMeta()
                t = TApp(Icit.Impl, t, m)
                va = vaf.cl.inst(eval(m))
                vaf = va.force(false)
            }
        }
        is MetaInsertion.UntilName -> {
            var vaf = va.force(false)
            while (vaf is VPi && vaf.icit == Icit.Impl) {
                if (vaf.n == mi.n) {
                    return t to va
                }
                val m = newMeta()
                t = TApp(Icit.Impl, t, m)
                va = vaf.cl.inst(eval(m))
                vaf = va.force(false)
            }
            throw ElabError(MontunoPure.top.loc, this, "No named arg ${mi.n}")
        }
    }
    return t to va
}

fun LocalContext.inferVar(n: String): Pair<Term, Val> {
    for (ni in nameTable[n].asReversed()) {
        return when {
            ni is NITop -> TTop(ni.lvl) to MontunoPure.top.topScope.entries[ni.lvl.it].type.second
            ni is NILocal && !ni.inserted -> TLocal(ni.lvl.toIx(lvl)) to types[ni.lvl.it]
            else -> continue
        }
    }
    throw ElabError(null, this, "Variable $n out of scope")
}

fun LocalContext.infer(mi: MetaInsertion, r: PreTerm): Pair<Term, Val> {
    MontunoPure.top.loc = r.loc
    return when (r) {
        is RU -> TU to VUnit
        is RNat -> TNat(r.n) to inferVar("Nat").first.eval(VEnv())
        is RVar -> insertMetas(mi, inferVar(r.n))
        is RStopMeta -> infer(MetaInsertion.No, r.body)
        is RForeign -> {
            val a = check(r.type, VUnit)
            TForeign(r.lang, r.eval, a) to eval(a)
        }
        is RHole -> {
            val m1 = newMeta()
            val m2 = newMeta()
            m1 to eval(m2)
        }
        is RPi -> {
            val a = check(r.type, VUnit)
            val b = localBindSrc(r.loc, r.n, eval(a)).check(r.body, VUnit)
            TPi(r.n, r.icit, a, b) to VUnit
        }
        is RFun -> TFun(check(r.l, VUnit), check(r.r, VUnit)) to VUnit
        is RLet -> {
            val a = check(r.type, VUnit)
            val gva = eval(a)
            val t = check(r.defn, gva)
            val gvt = eval(t)
            val (u, gvb) = localDefine(r.loc, r.n, gvt, gva).infer(mi, r.body)
            TLet(r.n, a, t, u) to gvb
        }
        is RApp -> {
            val ins = when (r.ni) {
                is NIName -> MetaInsertion.UntilName(r.ni.n)
                NIImpl -> MetaInsertion.No
                NIExpl -> MetaInsertion.Yes
            }
            val (t, va) = infer(ins, r.rator)
            insertMetas(mi, when (val v = va.force(true)) {
                is VPi -> {
                    when {
                        r.ni is NIExpl && v.icit != Icit.Expl -> { MontunoPure.top.loc = r.loc; throw ElabError(r.loc, this, "AppIcit") }
                        r.ni is NIImpl && v.icit != Icit.Impl -> { MontunoPure.top.loc = r.loc; throw ElabError(r.loc, this, "AppIcit") }
                    }
                    val u = check(r.rand, v.ty)
                    TApp(v.icit, t, u) to v.cl.inst(eval(u))
                }
                is VFun -> {
                    if (r.ni !is NIExpl) throw ElabError(r.loc, this, "Icit mismatch")
                    val u = check(r.rand, v.a)
                    TApp(Icit.Expl, t, u) to v.b
                }
                else -> throw ElabError(r.loc, this, "function expected, instead got $v")
            })
        }
        is RLam -> {
            val icit = when (r.ni) {
                NIImpl -> Icit.Impl
                NIExpl -> Icit.Expl
                is NIName -> throw ElabError(r.loc, this, "named lambda")
            }
            val va = eval(newMeta())
            val (t, vb) = localBindSrc(r.loc, r.n, va).infer(MetaInsertion.Yes, r.body)
            val b = vb.quote(lvl + 1)
            insertMetas(mi, TLam(r.n, icit, t) to VPi(r.n, icit, va, VCl(vals, b)))
        }
    }
}

fun checkTopLevel(e: TopLevel): Any? {
    MontunoPure.top.metas.add(mutableListOf())
    val ctx = LocalContext(MontunoPure.top.ntbl)
    MontunoPure.top.loc = e.loc
    val ntbl = MontunoPure.top.ntbl
    return when (e) {
        is RTerm -> when (e.cmd) {
            Pragma.ParseOnly -> e.tm.toString()
            Pragma.Reset -> { MontunoPure.top.reset(); null }
            Pragma.Symbols -> MontunoPure.top.topScope.entries.map { it.name }.toTypedArray()
            Pragma.WholeProgram -> { MontunoPure.top.printElaborated(); null }
            Pragma.Nothing -> ctx.infer(MetaInsertion.No, e.tm!!).first.pretty(NameEnv(ntbl)).toString()
            Pragma.Type -> {
                val (_, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ty.force(false).quote(Lvl(0)).pretty(NameEnv(ntbl)).toString()
            }
            Pragma.NormalType -> {
                val (_, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ty.force(true).quote(Lvl(0), true).pretty(NameEnv(ntbl)).toString()
            }
            Pragma.Elaborate -> {
                val (tm, _) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.eval(tm).force(false).quote((Lvl(0))).pretty(NameEnv(ntbl)).toString()
            }
            Pragma.Normalize -> {
                val (tm, _) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.eval(tm).force(true).quote(Lvl(0), true).pretty(NameEnv(ntbl)).toString()
            }
        }
        is RDecl -> {
            var a = ctx.check(e.ty, VUnit)
            ctx.simplifyMetaBlock()
            a = a.inline(Lvl(0), VEnv())
            MontunoPure.top.addTopLevel(e.n, e.loc, null, a)
            return null
        }
        is RDefn -> {
            var a: Term = if (e.ty != null) {
                ctx.check(e.ty, VUnit)
            } else try {
                ctx.inferVar(e.n).second.quote(Lvl(0))
            } catch (e: ElabError) {
                TU
            }
            var t = ctx.check(e.tm, ctx.eval(a))
            ctx.simplifyMetaBlock()
            a = a.inline(Lvl(0), VEnv())
            t = t.inline(Lvl(0), VEnv())
            MontunoPure.top.addTopLevel(e.n, e.loc, t, a)
            return null
        }
    }
}
