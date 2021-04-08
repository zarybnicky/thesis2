package montuno.interpreter

class UnifyError(val reason: String) : RuntimeException(reason)
class ElabError(val ctx: LocalContext, val reason: String) : RuntimeException(reason)
