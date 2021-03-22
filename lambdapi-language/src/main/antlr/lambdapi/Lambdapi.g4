grammar Lambdapi;

@header {
package lambdapi;
}

term
    : 'let' name=IDENT ':' type=term '=' tm=term 'in' body=term #Let
//    | 'if' ifE=term 'then' thenE=term 'else' elseE=term #If
    | '\\' (args+=bind)* '->' body=term #Lam
    | fun=infix (args+=infix)+ #App
    | exp=infix #Exp
//    | name=infix ':' type=term #Ann
    ;
infix
    : '(' rec=term ')' #Rec
    | IDENT ('->' rest=infix)? #Arr
//    | LIT #Lit
    ;
bind
    : IDENT
    | '(' name=IDENT ':' type=term ')'
//    | '_'
    ;

//LIT   : [0-9]+;
IDENT : [a-zA-Z] [a-zA-Z0-9]*;
WS    : [ \t\r\n] -> skip;
COMMENT : '--' (~[\r\n])* -> skip;
NCOMMENT : '{-'~[#] .*? '-}' -> skip;
