package scalasql.dialects

import scalasql.operations.{CaseWhen, DbApiOps}
import scalasql.query.SqlWindow
import scalasql.core.Db.apply0
import scalasql.{Table, operations}
import scalasql.core.Aggregatable
import scalasql.core.{DbApi, DialectTypeMappers, JoinNullable, Queryable, Db, SqlStr, TypeMapper}

import java.sql.{JDBCType, PreparedStatement, ResultSet}
import java.time.{
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  OffsetDateTime,
  OffsetTime,
  ZoneId,
  ZonedDateTime
}
import java.util.UUID
import scala.reflect.ClassTag

/**
 * Base type for all SQL dialects. A Dialect proides extension methods, extension operators,
 * and custom implementations of various query classes that may differ between databases
 */
trait Dialect extends DialectTypeMappers {
  implicit val dialectSelf: Dialect = this

  implicit def StringType: TypeMapper[String] = new StringType
  class StringType extends TypeMapper[String] {
    def jdbcType = JDBCType.LONGVARCHAR
    def get(r: ResultSet, idx: Int) = r.getString(idx)
    def put(r: PreparedStatement, idx: Int, v: String) = r.setString(idx, v)
  }

  implicit def ByteType: TypeMapper[Byte] = new ByteType
  class ByteType extends TypeMapper[Byte] {
    def jdbcType = JDBCType.TINYINT
    def get(r: ResultSet, idx: Int) = r.getByte(idx)
    def put(r: PreparedStatement, idx: Int, v: Byte) = r.setByte(idx, v)
  }

  implicit def ShortType: TypeMapper[Short] = new ShortType
  class ShortType extends TypeMapper[Short] {
    def jdbcType = JDBCType.SMALLINT
    def get(r: ResultSet, idx: Int) = r.getShort(idx)
    def put(r: PreparedStatement, idx: Int, v: Short) = r.setShort(idx, v)
  }

  implicit def IntType: TypeMapper[Int] = new IntType
  class IntType extends TypeMapper[Int] {
    def jdbcType = JDBCType.INTEGER
    def get(r: ResultSet, idx: Int) = r.getInt(idx)
    def put(r: PreparedStatement, idx: Int, v: Int) = r.setInt(idx, v)
  }

  implicit def LongType: TypeMapper[Long] = new LongType
  class LongType extends TypeMapper[Long] {
    def jdbcType = JDBCType.BIGINT
    def get(r: ResultSet, idx: Int) = r.getLong(idx)
    def put(r: PreparedStatement, idx: Int, v: Long) = r.setLong(idx, v)
  }

  implicit def DoubleType: TypeMapper[Double] = new DoubleType
  class DoubleType extends TypeMapper[Double] {
    def jdbcType = JDBCType.DOUBLE
    def get(r: ResultSet, idx: Int) = r.getDouble(idx)
    def put(r: PreparedStatement, idx: Int, v: Double) = r.setDouble(idx, v)
  }

  implicit def BigDecimalType: TypeMapper[scala.math.BigDecimal] = new BigDecimalType
  class BigDecimalType extends TypeMapper[scala.math.BigDecimal] {
    def jdbcType = JDBCType.DOUBLE
    def get(r: ResultSet, idx: Int) = r.getBigDecimal(idx)
    def put(r: PreparedStatement, idx: Int, v: scala.math.BigDecimal) = r
      .setBigDecimal(idx, v.bigDecimal)
  }

  implicit def BooleanType: TypeMapper[Boolean] = new BooleanType
  class BooleanType extends TypeMapper[Boolean] {
    def jdbcType = JDBCType.BOOLEAN
    def get(r: ResultSet, idx: Int) = r.getBoolean(idx)
    def put(r: PreparedStatement, idx: Int, v: Boolean) = r.setBoolean(idx, v)
  }

  implicit def UuidType: TypeMapper[UUID] = new UuidType
  class UuidType extends TypeMapper[UUID] {
    def jdbcType = JDBCType.VARBINARY

    def get(r: ResultSet, idx: Int) = {
      r.getObject(idx) match {
        case u: UUID => u
        case s: String => UUID.fromString(s)
      }
    }

    def put(r: PreparedStatement, idx: Int, v: UUID) = {
      r.setObject(idx, v)
    }
  }

  implicit def BytesType: TypeMapper[geny.Bytes] = new BytesType
  class BytesType extends TypeMapper[geny.Bytes] {
    def jdbcType = JDBCType.VARBINARY
    def get(r: ResultSet, idx: Int) = new geny.Bytes(r.getBytes(idx))
    def put(r: PreparedStatement, idx: Int, v: geny.Bytes) = r.setBytes(idx, v.array)
  }

  implicit def LocalDateType: TypeMapper[LocalDate] = new LocalDateType
  class LocalDateType extends TypeMapper[LocalDate] {
    def jdbcType = JDBCType.DATE
    def get(r: ResultSet, idx: Int) = r.getObject(idx, classOf[LocalDate])
    def put(r: PreparedStatement, idx: Int, v: LocalDate) = r.setObject(idx, v)
  }

  implicit def LocalTimeType: TypeMapper[LocalTime] = new LocalTimeType
  class LocalTimeType extends TypeMapper[LocalTime] {
    def jdbcType = JDBCType.TIME
    def get(r: ResultSet, idx: Int) = r.getObject(idx, classOf[LocalTime])
    def put(r: PreparedStatement, idx: Int, v: LocalTime) = r.setObject(idx, v)
  }

  implicit def LocalDateTimeType: TypeMapper[LocalDateTime] = new LocalDateTimeType
  class LocalDateTimeType extends TypeMapper[LocalDateTime] {
    def jdbcType = JDBCType.TIMESTAMP
    def get(r: ResultSet, idx: Int) = r.getObject(idx, classOf[LocalDateTime])
    def put(r: PreparedStatement, idx: Int, v: LocalDateTime) = r.setObject(idx, v)
  }

  implicit def ZonedDateTimeType: TypeMapper[ZonedDateTime] = new ZonedDateTimeType
  class ZonedDateTimeType extends TypeMapper[ZonedDateTime] {
    def jdbcType = JDBCType.TIMESTAMP_WITH_TIMEZONE
    override def castTypeString = "TIMESTAMP WITH TIME ZONE"
    def get(r: ResultSet, idx: Int) = r.getTimestamp(idx).toInstant.atZone(ZoneId.systemDefault())
    def put(r: PreparedStatement, idx: Int, v: ZonedDateTime) = r
      .setTimestamp(idx, java.sql.Timestamp.from(v.toInstant))
  }

  implicit def InstantType: TypeMapper[Instant] = new InstantType
  class InstantType extends TypeMapper[Instant] {
    def jdbcType = JDBCType.TIMESTAMP

    def get(r: ResultSet, idx: Int) = {
      r.getObject(idx) match {
        // Sqlite sometimes returns this
        case l: java.lang.Long => Instant.ofEpochMilli(l)
        // Sqlite sometimes also returns this
        case s: java.lang.String => java.sql.Timestamp.valueOf(s).toInstant
        // H2 and HsqlDb return this
        case o: java.time.OffsetDateTime => o.toInstant
        // MySql returns this
        case l: java.time.LocalDateTime =>
          l.toInstant(ZoneId.systemDefault().getRules().getOffset(Instant.now()))
        // Everyone seems to return this sometimes
        case l: java.sql.Timestamp => l.toInstant()
      }
    }

    def put(r: PreparedStatement, idx: Int, v: Instant) = r
      .setTimestamp(idx, java.sql.Timestamp.from(v))
  }

  implicit def OffsetTimeType: TypeMapper[OffsetTime] = new OffsetTimeType
  class OffsetTimeType extends TypeMapper[OffsetTime] {
    def jdbcType = JDBCType.TIME_WITH_TIMEZONE
    override def castTypeString = "TIME WITH TIME ZONE"
    def get(r: ResultSet, idx: Int) = r.getObject(idx, classOf[OffsetTime])
    def put(r: PreparedStatement, idx: Int, v: OffsetTime) = r.setObject(idx, v)
  }

  implicit def OffsetDateTimeType: TypeMapper[OffsetDateTime] = new OffsetDateTimeType
  class OffsetDateTimeType extends TypeMapper[OffsetDateTime] {
    def jdbcType = JDBCType.TIMESTAMP_WITH_TIMEZONE
    override def castTypeString = "TIMESTAMP WITH TIME ZONE"
    def get(r: ResultSet, idx: Int) = {
      r.getTimestamp(idx).toInstant.atOffset(OffsetDateTime.now().getOffset)
    }
    def put(r: PreparedStatement, idx: Int, v: OffsetDateTime) = {
      r.setTimestamp(idx, java.sql.Timestamp.from(v.toInstant))
    }
  }

  implicit def EnumType[T <: Enumeration#Value](implicit constructor: String => T): TypeMapper[T] =
    new EnumType[T]
  class EnumType[T](implicit constructor: String => T) extends TypeMapper[T] {
    def jdbcType: JDBCType = JDBCType.VARCHAR
    def get(r: ResultSet, idx: Int): T = constructor(r.getString(idx))
    def put(r: PreparedStatement, idx: Int, v: T) = r.setObject(idx, v, java.sql.Types.OTHER)
  }

  implicit def from(x: Int): Db[Int] = Db(x)

  implicit def from(x: Long): Db[Long] = Db(x)

  implicit def from(x: Boolean): Db[Boolean] = Db.apply0(x, x)

  implicit def from(x: Double): Db[Double] = Db(x)

  implicit def from(x: scala.math.BigDecimal): Db[scala.math.BigDecimal] = Db(x)

  implicit def from(x: String): Db[String] = Db(x)

  implicit def OptionType[T](implicit inner: TypeMapper[T]): TypeMapper[Option[T]] =
    new TypeMapper[Option[T]] {
      def jdbcType: JDBCType = inner.jdbcType

      def get(r: ResultSet, idx: Int): Option[T] = {
        if (r.getObject(idx) == null) None else Some(inner.get(r, idx))
      }

      def put(r: PreparedStatement, idx: Int, v: Option[T]): Unit = {
        v match {
          case None => r.setNull(idx, jdbcType.getVendorTypeNumber)
          case Some(value) => inner.put(r, idx, value)
        }
      }
    }
  implicit def DbBooleanOpsConv(v: Db[Boolean]): operations.DbBooleanOps =
    new operations.DbBooleanOps(v)
  implicit def DbNumericOpsConv[T: Numeric: TypeMapper](
      v: Db[T]
  ): operations.DbNumericOps[T] = new operations.DbNumericOps(v)

  implicit def DbOpsConv(v: Db[_]): operations.DbOps = new operations.DbOps(v)

  implicit def DbTypedOpsConv[T: ClassTag](v: Db[T]): operations.DbTypedOps[T] =
    new operations.DbTypedOps(v)

  implicit def DbOptionOpsConv[T: TypeMapper](v: Db[Option[T]]): operations.DbOptionOps[T] =
    new operations.DbOptionOps(v)

  implicit def JoinNullableOpsConv[T: TypeMapper](v: JoinNullable[Db[T]]): operations.DbOps =
    new operations.DbOps(JoinNullable.toExpr(v))

  implicit def JoinNullableOptionOpsConv[T: TypeMapper](
      v: JoinNullable[Db[T]]
  ): operations.DbOptionOps[T] =
    new operations.DbOptionOps(JoinNullable.toExpr(v))

  implicit def DbStringOpsConv(
      v: Db[String]
  ): operations.DbStringLikeOps[String] with operations.DbStringOps[String]
  implicit def DbBlobOpsConv(v: Db[geny.Bytes]): operations.DbStringLikeOps[geny.Bytes]

  implicit def AggNumericOpsConv[V: Numeric: TypeMapper](v: Aggregatable[Db[V]])(
      implicit qr: Queryable.Row[Db[V], V]
  ): operations.AggNumericOps[V] = new operations.AggNumericOps(v)

  implicit def AggOpsConv[T](v: Aggregatable[T])(
      implicit qr: Queryable.Row[T, _]
  ): operations.AggOps[T] = new operations.AggOps(v)

  implicit def DbAggOpsConv[T](v: Aggregatable[Db[T]]): operations.DbAggOps[T]

  implicit def TableOpsConv[V[_[_]]](t: Table[V]): TableOps[V] = new TableOps(t)
  implicit def DbApiQueryOpsConv(db: => DbApi): DbApiQueryOps = new DbApiQueryOps(this)
  implicit def DbApiOpsConv(db: => DbApi): DbApiOps = new DbApiOps(this)

  implicit class WindowExtensions[T](e: Db[T]) {
    def over = new SqlWindow[T](e, None, None, Nil, None, None, None)
  }
  // This is necessary for `runSql` to work.
  implicit def DbQueryable[T](implicit mt: TypeMapper[T]): Queryable.Row[Db[T], T] = {
    new Db.SqlQueryable[Db, T]()
  }
}
