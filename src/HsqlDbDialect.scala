package usql
import usql.query.Expr
import usql.renderer.SqlStr.SqlStringSyntax

object HsqlDbDialect extends SqliteDialect {
  class ExprStringOps(v: Expr[String]) extends operations.ExprStringOps(v) {

    def indexOf(x: Expr[String]): Expr[Int] = Expr { implicit ctx => usql"INSTR($v, $x)" }
    def glob(x: Expr[String]): Expr[Int] = Expr { implicit ctx => usql"GLOB($v, $x)" }

    def ltrim(x: Expr[String]): Expr[String] = Expr { implicit ctx => usql"LTRIM($v, $x)" }
    def rtrim(x: Expr[String]): Expr[String] = Expr { implicit ctx => usql"RTRIM($v, $x)" }

  }

  class ExprNumericOps[T: Numeric](v: Expr[T]) extends operations.ExprNumericOps[T](v){
    override def &[V: Numeric](x: Expr[V]): Expr[T] = Expr { implicit ctx => usql"BITAND($v, $x)" }

    override def |[V: Numeric](x: Expr[V]): Expr[T] = Expr { implicit ctx => usql"BITOR($v, $x)" }

    override def unary_~ : Expr[T] = Expr { implicit ctx => usql"BITNOT($v)" }

    override def unary_- : Expr[T] = Expr { implicit ctx => usql"-($v)" }
  }
}
trait HsqlDbDialect extends Dialect {
  override implicit def ExprStringOpsConv(v: Expr[String]): HsqlDbDialect.ExprStringOps =
    new HsqlDbDialect.ExprStringOps(v)
  override implicit def ExprNumericOpsConv[T: Numeric](v: Expr[T]): HsqlDbDialect.ExprNumericOps[T] =
    new HsqlDbDialect.ExprNumericOps(v)
}
