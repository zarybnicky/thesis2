package montuno.interpreter

import montuno.Lvl
import montuno.Meta
import montuno.UnifyError
import montuno.interpreter.scope.MetaEntry

data class Renaming(val domain: Lvl, val codomain: Lvl, val ren: Map<Lvl, Lvl>) {
    fun lift(): Renaming = Renaming(domain + 1, codomain + 1, ren + (domain to codomain))
}
fun fromSpine(init: Lvl, spine: VSpine): Renaming {
    var dom = Lvl(0)
    val ren = mutableMapOf<Lvl, Lvl>()
    for (sp in spine.it) when (sp) {
        is SApp -> {
            val v = sp.v.forceUnfold()
            if (v !is VLocal || ren.containsKey(v.head)) throw UnifyError("pattern condition failed")
            ren[v.head] = dom
            dom += 1
        }
        else -> throw UnifyError("pattern condition failed")
    }
    return Renaming(dom, init, ren)
}

fun Term.lams(lvl: Lvl, type: Val): Term {
    var t = this
    var l = Lvl(0)
    var ty = type
    while (l.it != lvl.it) when (ty) {
        is VPi -> {
            t = TLam(ty.name, ty.icit, ty.bound.quote(l, false), t)
            ty = ty.closure.inst(VLocal(l))
            l += 1
        }
        is VLam -> {
            t = TLam(ty.name, ty.icit, ty.bound.quote(l, false), t)
            ty = ty.closure.inst(VLocal(l))
            l += 1
        }
        else -> TODO("impossible")
    }
    return t
}

enum class ConvState { Rigid, Flex, Full }

fun Term.renameSp(occurs: Meta, state: ConvState, ren: Renaming, spine: VSpine): Term {
    return spine.it.reversed().fold(this) { l, it -> when (it) {
        SProj1 -> TProj1(l)
        SProj2 -> TProj2(l)
        is SProjF -> TProjF(it.n, l, it.i)
        is SApp -> TApp(it.icit, l, it.v.rename(occurs, state, ren))
    } }
}
fun Val.rename(occurs: Meta, state: ConvState, ren: Renaming): Term {
    return when (val v = if (state == ConvState.Full) forceUnfold() else forceMeta()) {
        is VTop -> TTop(v.head, v.slot).renameSp(occurs, ConvState.Flex, ren, v.spine)
        is VLocal -> {
            if (!ren.ren.containsKey(v.head)) throw UnifyError("Non-local variable")
            TLocal(ren.ren[v.head]!!.toIx(ren.domain)).renameSp(occurs, ConvState.Flex, ren, v.spine)
        }
        is VMeta -> when {
            v.head == occurs -> throw UnifyError("Occurs check failed")
            !v.slot.solved -> TMeta(v.head, v.slot, emptyArray()).renameSp(occurs, ConvState.Flex, ren, v.spine)
            v.slot.solved && state == ConvState.Rigid -> try {
                TMeta(v.head, v.slot, emptyArray()).renameSp(occurs, ConvState.Flex, ren, v.spine)
            } catch (e: UnifyError) {
                v.slot.value!!.rename(occurs, ConvState.Full, ren)
            }
            v.slot.solved && state == ConvState.Flex -> throw UnifyError("Occurs check failed")
            else -> TODO("impossible")
        }
        is VLam -> TLam(v.name, v.icit, v.bound.rename(occurs, state, ren), v.closure.inst(VLocal(ren.codomain)).rename(occurs, state, ren.lift()))
        is VPi -> TPi(v.name, v.icit, v.bound.rename(occurs, state, ren), v.closure.inst(VLocal(ren.codomain)).rename(occurs, state, ren.lift()))
        is VSg -> TSg(v.name, v.bound.rename(occurs, state, ren), v.closure.inst(VLocal(ren.codomain)).rename(occurs, state, ren.lift()))
        is VPair -> TPair(v.left.rename(occurs, state, ren), v.right.rename(occurs, state, ren))
        VUnit -> TUnit
        is VNat -> TNat(v.n)
        is VBool -> TBool(v.n)
        is VThunk -> v.value.rename(occurs, state, ren)
    }
}

fun LocalContext.solve(lvl: Lvl, state: ConvState, meta: MetaEntry, sp: VSpine, v: Val) {
    if (meta.solved) throw UnifyError("Trying to solve already solved meta")
    if (state == ConvState.Flex) throw UnifyError("")
    val ren = fromSpine(lvl, sp)
    val rhs = v.rename(meta.meta, state, ren)
    val solution = rhs.lams(ren.domain, meta.type).eval(ctx, VEnv())
    ctx.registerMeta(meta.meta, solution)
}

fun LocalContext.unifySp(lvl: Lvl, state: ConvState, a: VSpine, b: VSpine) {
    if (a.it.size != b.it.size) throw UnifyError("spines differ")
    for ((aa, bb) in a.it.zip(b.it)) when {
        aa == SProj1 && bb == SProj1 -> {}
        aa == SProj2 && bb == SProj2 -> {}
        aa is SProjF && bb is SProjF -> if (aa.i != bb.i) throw UnifyError("spines differ")
        aa is SApp && bb is SApp -> unify(lvl, state, aa.v, bb.v)
        else -> throw UnifyError("spines differ")
    }
}

fun LocalContext.unify(lvl: Lvl, state: ConvState, a: Val, b: Val) {
    val v = if (state == ConvState.Full) a.forceUnfold() else a.forceMeta()
    val w = if (state == ConvState.Full) b.forceUnfold() else b.forceMeta()
    when {
        v is VLocal && w is VLocal && v.head == w.head -> unifySp(lvl, state, v.spine, w.spine)
        v is VMeta && w is VMeta && v.head == w.head -> unifySp(lvl, state, v.spine, w.spine)
        v is VTop && w is VTop && v.head == w.head -> when (state) {
            ConvState.Rigid -> try {
                unifySp(lvl, state, v.spine, w.spine)
            } catch (e: UnifyError) {
                unify(lvl, ConvState.Full, v.slot.defnV!!, w.slot.defnV!!)
            }
            ConvState.Flex -> throw UnifyError("cannot unfold")
            ConvState.Full -> TODO("impossible")
        }
        v is VTop && v.slot.defnV != null -> when (state) {
            ConvState.Rigid -> unify(lvl, state, v.spine.applyTo(v.slot.defnV), w)
            ConvState.Flex -> throw UnifyError("cannot unfold")
            ConvState.Full -> TODO("impossible")
        }
        w is VTop && w.slot.defnV != null -> when (state) {
            ConvState.Rigid -> unify(lvl, state, w.spine.applyTo(w.slot.defnV), v)
            ConvState.Flex -> throw UnifyError("cannot unfold")
            ConvState.Full -> TODO("impossible")
        }
        v is VMeta && v.slot.solved -> when (state) {
            ConvState.Rigid -> unify(lvl, state, v.spine.applyTo(v.slot.value!!), w)
            ConvState.Flex -> throw UnifyError("cannot unfold")
            ConvState.Full -> TODO("impossible")
        }
        w is VMeta && w.slot.solved -> when (state) {
            ConvState.Rigid -> unify(lvl, state, w.spine.applyTo(w.slot.value!!), v)
            ConvState.Flex -> throw UnifyError("cannot unfold")
            ConvState.Full -> TODO("impossible")
        }

        v is VUnit && w is VUnit -> {}
        v is VNat && w is VNat && v.n == w.n -> {}
        v is VBool && w is VBool && v.n == w.n -> {}
        v is VPi && w is VPi && v.icit == w.icit -> {
            unify(lvl, state, v.bound, w.bound)
            unify(lvl + 1, state, v.closure.inst(VLocal(lvl)), w.closure.inst(VLocal(lvl)))
        }
        v is VSg && w is VSg -> {
            unify(lvl, state, v.bound, w.bound)
            unify(lvl + 1, state, v.closure.inst(VLocal(lvl)), w.closure.inst(VLocal(lvl)))
        }
        v is VLam && w is VLam -> unify(lvl + 1, state, v.closure.inst(VLocal(lvl)), w.closure.inst(VLocal(lvl)))
        v is VLam -> unify(lvl + 1, state, v.closure.inst(VLocal(lvl)), w.app(v.icit, VLocal(lvl)))
        w is VLam -> unify(lvl + 1, state, w.closure.inst(VLocal(lvl)), v.app(w.icit, VLocal(lvl)))
        v is VPair && w is VPair -> {
            unify(lvl, state, v.left, w.left)
            unify(lvl, state, v.right, w.right)
        }
        v is VPair -> {
            unify(lvl, state, v.left, w.proj1())
            unify(lvl, state, v.right, w.proj2())
        }
        w is VPair -> {
            unify(lvl, state, w.left, v.proj1())
            unify(lvl, state, w.right, v.proj2())
        }

        v is VMeta && !v.slot.solved -> solve(lvl, state, v.slot, v.spine, w)
        w is VMeta && !w.slot.solved -> solve(lvl, state, w.slot, w.spine, v)

        else -> throw UnifyError("failed to unify:\n$v\n$w")
    }
}
