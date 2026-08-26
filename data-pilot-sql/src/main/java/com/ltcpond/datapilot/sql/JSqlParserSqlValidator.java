package com.ltcpond.datapilot.sql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 使用 AST 识别语句类型和访问表，并对 MySQL 危险扩展执行额外拦截。 */
@Component
public class JSqlParserSqlValidator implements SqlValidator {

    private static final Set<String> SYSTEM_SCHEMAS = Set.of(
            "information_schema", "mysql", "performance_schema", "sys");
    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            pattern("\\bINTO\\s+(OUTFILE|DUMPFILE)\\b"),
            pattern("\\bFOR\\s+UPDATE\\b"),
            pattern("\\bLOCK\\s+IN\\s+SHARE\\s+MODE\\b"),
            pattern("\\b(SLEEP|BENCHMARK|LOAD_FILE|GET_LOCK|RELEASE_LOCK)\\s*\\("));
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
            "(?is)\\s+LIMIT\\s+(?:(\\d+)\\s*,\\s*)?(\\d+)(?:\\s+OFFSET\\s+(\\d+))?\\s*$");

    @Override
    public SqlValidationResult validate(SqlValidationRequest request) {
        List<String> violations = new ArrayList<>();
        if (request.sql() == null || request.sql().isBlank()) {
            return SqlValidationResult.rejected(List.of("EMPTY_SQL"));
        }
        if (request.maxRows() <= 0) {
            return SqlValidationResult.rejected(List.of("INVALID_ROW_LIMIT"));
        }

        String normalizedSql = stripTrailingSemicolon(request.sql().trim());
        rejectComments(normalizedSql, violations);
        rejectDangerousExtensions(normalizedSql, violations);

        try {
            List<Statement> statements = CCJSqlParserUtil.parseStatements(normalizedSql).getStatements();
            if (statements.size() != 1) {
                violations.add("MULTIPLE_STATEMENTS");
            } else if (!(statements.getFirst() instanceof Select)) {
                violations.add("NON_SELECT_STATEMENT");
            }
            if (violations.isEmpty()) {
                Statement statement = statements.getFirst();
                validateTables(statement, request.allowedTables(), violations);
                validateQualifiedColumns(statement, request.allowedColumns(), violations);
            }
        } catch (JSQLParserException exception) {
            violations.add("SQL_PARSE_ERROR");
        }

        if (!violations.isEmpty()) {
            return SqlValidationResult.rejected(violations.stream().distinct().toList());
        }
        return SqlValidationResult.accepted(enforceLimit(normalizedSql, request.maxRows()));
    }

    private void rejectComments(String sql, List<String> violations) {
        if (sql.contains("--") || sql.contains("/*") || sql.contains("*/")) {
            violations.add("SQL_COMMENTS_NOT_ALLOWED");
        }
    }

    private void rejectDangerousExtensions(String sql, List<String> violations) {
        for (Pattern forbiddenPattern : FORBIDDEN_PATTERNS) {
            if (forbiddenPattern.matcher(sql).find()) {
                violations.add("DANGEROUS_SQL_FEATURE");
                return;
            }
        }
    }

    private void validateTables(Statement statement, Set<String> allowedTables, List<String> violations) {
        Set<String> normalizedAllowed = new HashSet<>();
        allowedTables.forEach(table -> normalizedAllowed.add(normalizeIdentifier(table)));

        for (String table : new TablesNamesFinder<>().getTables(statement)) {
            String normalizedTable = normalizeIdentifier(table);
            String schema = schemaName(normalizedTable);
            if (SYSTEM_SCHEMAS.contains(schema)) {
                violations.add("SYSTEM_SCHEMA_ACCESS");
            } else if (!schema.isEmpty() && !normalizedAllowed.contains(normalizedTable)) {
                // 显式指定 Schema 时必须完整匹配，不能仅凭同名表绕过库级白名单。
                violations.add("UNAUTHORIZED_TABLE");
            } else if (schema.isEmpty() && !normalizedAllowed.contains(normalizedTable)) {
                violations.add("UNAUTHORIZED_TABLE");
            }
        }
    }

    /**
     * 校验带表前缀的字段。无前缀字段和 SELECT 输出别名交给数据库 EXPLAIN 判定，
     * 避免把 ORDER BY 中合法的输出别名误判为不存在字段。
     */
    private void validateQualifiedColumns(
            Statement statement,
            Map<String, Set<String>> allowedColumns,
            List<String> violations) {
        SchemaReferenceFinder finder = new SchemaReferenceFinder();
        finder.collect(statement);
        for (Column column : finder.columns()) {
            String qualifier = normalizeIdentifier(column.getTableName());
            if (qualifier.isBlank()) {
                continue;
            }
            String physicalTable = finder.physicalTable(qualifier);
            if (physicalTable == null) {
                // CTE 名称或派生表别名不对应物理表，其字段由 EXPLAIN 校验。
                continue;
            }
            Set<String> columns = allowedColumns.get(physicalTable);
            if (columns == null) {
                columns = allowedColumns.get(simpleTableName(physicalTable));
            }
            if (columns == null) {
                // CTE 或派生表的输出字段不属于物理 Schema 字段白名单。
                continue;
            }
            if (!columns.contains(normalizeIdentifier(column.getColumnName()))) {
                violations.add("UNKNOWN_COLUMN");
            }
        }
    }

    private String enforceLimit(String sql, int maxRows) {
        Matcher matcher = LIMIT_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return sql + " LIMIT " + maxRows;
        }

        BigInteger requestedRows = new BigInteger(matcher.group(2));
        if (requestedRows.compareTo(BigInteger.valueOf(maxRows)) <= 0) {
            return sql;
        }

        StringBuilder replacement = new StringBuilder(" LIMIT ");
        if (matcher.group(1) != null) {
            replacement.append(matcher.group(1)).append(", ").append(maxRows);
        } else {
            replacement.append(maxRows);
            if (matcher.group(3) != null) {
                replacement.append(" OFFSET ").append(matcher.group(3));
            }
        }
        return sql.substring(0, matcher.start()) + replacement;
    }

    private String stripTrailingSemicolon(String sql) {
        return sql.endsWith(";") ? sql.substring(0, sql.length() - 1).trim() : sql;
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        return identifier.replace("`", "").replace("\"", "").toLowerCase(Locale.ROOT);
    }

    private String schemaName(String table) {
        int separator = table.indexOf('.');
        return separator < 0 ? "" : table.substring(0, separator);
    }

    private String simpleTableName(String table) {
        int separator = table.lastIndexOf('.');
        return separator < 0 ? table : table.substring(separator + 1);
    }

    private static Pattern pattern(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    /** 复用 JSqlParser 的完整遍历能力，同时记录物理表别名和字段引用。 */
    private final class SchemaReferenceFinder extends TablesNamesFinder<Void> {

        private final Set<Column> columns = new HashSet<>();
        private final Map<String, String> aliases = new java.util.HashMap<>();

        void collect(Statement statement) {
            init(true);
            statement.accept(this, null);
        }

        Set<Column> columns() {
            return Set.copyOf(columns);
        }

        String physicalTable(String qualifier) {
            return aliases.get(qualifier);
        }

        @Override
        public <S> Void visit(Table table, S context) {
            String name = normalizeIdentifier(table.getName());
            String schema = normalizeIdentifier(table.getSchemaName());
            String physicalName = schema.isBlank() ? name : schema + "." + name;
            aliases.put(name, physicalName);
            aliases.put(physicalName, physicalName);
            if (table.getAlias() != null) {
                aliases.put(normalizeIdentifier(table.getAlias().getName()), physicalName);
            }
            return super.visit(table, context);
        }

        @Override
        public <S> Void visit(Column column, S context) {
            columns.add(column);
            return super.visit(column, context);
        }
    }
}
