package montuno.common

import montuno.syntax.Loc
import montuno.syntax.PreTerm

interface ValFactory<T, V> {
    fun top(lvl: Lvl, slot: TopEntry<T, V>): V
    fun meta(meta: Meta, slot: MetaEntry<T, V>): V
    fun local(ix: Lvl): V
    fun unit(): V
    fun nat(n: Int): V
}

interface TermFactory<T, V> {
    fun top(lvl: Lvl, slot: TopEntry<T, V>): T
    fun meta(meta: Meta, slot: MetaEntry<T, V>): T
    fun local(ix: Ix): T
    fun app(icit: Icit, l: T, r: T): T
    fun unit(): T
    fun nat(n: Int): T
}

abstract class LocalLevelContext<T, V> {
    abstract val top: TopLevelContext<T, V>
    abstract val env: LocalEnv<V>
    abstract fun bind(loc: Loc, n: String, inserted: Boolean, ty: V): LocalLevelContext<T, V>
    abstract fun define(loc: Loc, n: String, tm: V, ty: V): LocalLevelContext<T, V>

    abstract fun infer(mi: MetaInsertion, e: PreTerm): Pair<T, V>
    abstract fun inferVar(n: String): Pair<T, V>
    abstract fun check(e: PreTerm, v: V): T
    abstract fun eval(t: T): V
    abstract fun quote(v: V, unfold: Boolean, depth: Lvl = Lvl(0)): T
    abstract fun force(v: V, unfold: Boolean): V
    fun newMeta() = top.newMeta(env.lvl, env.boundLevels)

    abstract fun pretty(t: T): String
    abstract fun inline(t: T): T
    abstract fun isUnfoldable(t: T): Boolean
    abstract fun markOccurs(occurs: IntArray, blockIx: Int, t: T)
}

abstract class TopLevelContext<T, V> {
    var ntbl = NameTable()
    abstract val topScope: TopLevelScope<T, V>
    abstract val valFactory: ValFactory<T, V>
    abstract val termFactory: TermFactory<T, V>
    abstract fun makeLocalContext(): LocalLevelContext<T, V>

    var loc: Loc = Loc.Unavailable
    var metas: MutableList<MutableList<MetaEntry<T, V>>> = mutableListOf()

    fun reset() {
        topScope.entries.removeAll { true }
        metas = mutableListOf()
        loc = Loc.Unavailable
        ntbl = NameTable()
    }

    operator fun get(lvl: Lvl) = topScope.entries[lvl.it]
    operator fun get(meta: Meta) = metas[meta.i][meta.j]
    operator fun set(meta: Meta, tm: T) {
        val ctx = makeLocalContext()
        this[meta].solve(ctx.eval(tm), tm, ctx.isUnfoldable(tm))
    }
    operator fun set(meta: Meta, m: MetaEntry<T, V>) {
        val (i, j) = meta
        if (metas[i].size == j) metas[i].add(m) else metas[i][j] = m
    }
    fun rigidity(lvl: Lvl) = if (this[lvl].defn == null) Rigidity.Rigid else Rigidity.Flex
    fun rigidity(meta: Meta) = if (this[meta].solved) Rigidity.Flex else Rigidity.Rigid

    fun metaVal(meta: Meta): V = metas[meta.i][meta.j].let { if (it.solved) it.value!! else valFactory.meta(meta, metas[meta.i][meta.j]) }
    fun topVal(lvl: Lvl): V = when (val top = topScope.entries[lvl.it].defnV) {
        null -> valFactory.top(lvl, topScope.entries[lvl.it])
        else -> top
    }

    fun newMetaBlock() = metas.add(mutableListOf())
    fun newMeta(depth: Lvl, boundLevels: IntArray): T {
        val i = metas.size - 1
        val meta = Meta(i, metas[i].size)
        this[meta] = MetaEntry(loc)
        var ret = termFactory.meta(meta, this[meta])
        for (l in boundLevels) {
            ret = termFactory.app(Icit.Expl, ret, termFactory.local(Lvl(l).toIx(depth)))
        }
        return ret
    }
    fun simplifyMetaBlock() {
        if (metas.size == 0) return
        val blockIx = metas.size - 1
        val block = metas[blockIx]
        val occurs = IntArray(block.size) { 0 }

        // 1. Inline already inlinable metas in block, check for unsolved metas
        // 2. Mark metas which don't occur in other metas as inlinable
        val ctx = makeLocalContext()
        for ((ix, meta) in block.withIndex()) when {
            !meta.solved -> throw ElabError(meta.loc, "Unsolved meta ${Meta(blockIx, ix)}")
            else -> {
                meta.term = ctx.inline(meta.term!!)
                meta.value = ctx.eval(meta.term!!)
                meta.unfoldable = ctx.isUnfoldable(meta.term!!)
                ctx.markOccurs(occurs, blockIx, meta.term!!)
            }
        }
        for ((ix, meta) in block.withIndex()) {
            if (!meta.solved) throw ElabError(meta.loc, "Unsolved meta ${Meta(blockIx, ix)}")
            if (!meta.unfoldable && occurs[ix] == 0) {
                block[ix].unfoldable = true
            }
        }
    }

    fun printElaborated() {
        val ctx = makeLocalContext()
        for ((i, topMeta) in metas.zip(topScope.entries).withIndex()) {
            val (metaBlock, topEntry) = topMeta
            for ((j, meta) in metaBlock.withIndex()) {
                if (!meta.solved) throw UnifyError("Unsolved metablock")
                if (meta.unfoldable) continue
                println("  $i.$j = ${ctx.pretty(meta.term!!)}")
            }
            when (topEntry.defn) {
                null -> println("${topEntry.name} : ${ctx.pretty(topEntry.type)}")
                else -> {
                    println("${topEntry.name} : ${ctx.pretty(topEntry.type)} =")
                    println("${" ".repeat(topEntry.name.length)}${ctx.pretty(topEntry.defn)}")
                }
            }
        }
    }

    fun getSymbols(): Array<String> = topScope.entries.map { it.name }.toTypedArray()
    fun addTopLevel(n: String, l: Loc, t: T?, a: T) {
        val ctx = makeLocalContext()
        ntbl.addName(n, NITop(l, Lvl(topScope.entries.size)))
        topScope.entries.add(TopEntry(l, n, t, if (t != null) ctx.eval(t) else null, a, ctx.eval(a)))
    }
}
