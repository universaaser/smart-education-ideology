param(
    [string]$Database = "smart_education",
    [string]$User = "root",
    [string]$Password = $env:SMART_EDU_DB_PASSWORD,
    [string]$HostName = "localhost",
    [int]$Port = 3306,
    [string]$MysqlPath = "mysql",
    [string]$MysqldumpPath = "mysqldump",
    [string]$MigrationsDir = (Join-Path $PSScriptRoot "..\backend\src\main\resources"),
    [string]$BackupDir = (Join-Path $PSScriptRoot "..\Draft\db-backups"),
    [switch]$DryRun,
    [switch]$SkipBackup,
    [switch]$InitIfMissing
)

$ErrorActionPreference = "Stop"

function Get-MySqlArgs {
    param([string]$DbName)

    $args = @(
        "--protocol=TCP",
        "--host=$HostName",
        "--port=$Port",
        "--user=$User",
        "--default-character-set=utf8mb4",
        "--batch",
        "--skip-column-names"
    )
    if ($DbName) {
        $args += $DbName
    }
    return $args
}

function Invoke-MySqlNative {
    param(
        [string]$CommandPath,
        [string[]]$Arguments
    )

    $hadMysqlPwd = Test-Path Env:MYSQL_PWD
    $previousMysqlPwd = $env:MYSQL_PWD
    $previousErrorActionPreference = $ErrorActionPreference
    if ($Password) {
        $env:MYSQL_PWD = $Password
    }
    try {
        $ErrorActionPreference = "Continue"
        $output = & $CommandPath @Arguments 2>&1
        return @{
            ExitCode = $LASTEXITCODE
            Output = @($output | Where-Object { "$_" -notmatch "^(mysql|mysqldump): \[Warning\]" })
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
        if ($hadMysqlPwd) {
            $env:MYSQL_PWD = $previousMysqlPwd
        } else {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        }
    }
}

function Invoke-MySqlQuery {
    param(
        [string]$Sql,
        [string]$DbName = $null
    )

    $args = Get-MySqlArgs -DbName $DbName
    $args += "--execute=$Sql"
    $result = Invoke-MySqlNative -CommandPath $MysqlPath -Arguments $args
    if ($result.ExitCode -ne 0) {
        throw "mysql query failed: $($result.Output -join [Environment]::NewLine)"
    }
    return @($result.Output | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne "" })
}

function Invoke-MySqlFile {
    param([string]$FilePath)

    $mysqlFilePath = (Resolve-Path $FilePath).Path.Replace("\", "/")
    $args = Get-MySqlArgs -DbName $Database
    $args += "--execute=source $mysqlFilePath"
    $result = Invoke-MySqlNative -CommandPath $MysqlPath -Arguments $args
    if ($result.ExitCode -ne 0) {
        throw "mysql migration failed for $FilePath`: $($result.Output -join [Environment]::NewLine)"
    }
}

function Test-Sql {
    param([string]$Sql)

    $result = Invoke-MySqlQuery -Sql $Sql -DbName $Database
    return ($result.Count -gt 0 -and $result[0] -eq "1")
}

function Get-FileChecksum {
    param([string]$FilePath)

    return (Get-FileHash -Algorithm SHA256 -Path $FilePath).Hash.ToLowerInvariant()
}

function Set-MigrationApplied {
    param(
        [string]$FileName,
        [string]$Checksum,
        [string]$Mode
    )

    $sql = @"
INSERT INTO schema_migrations (filename, checksum, applied_mode)
VALUES ('$FileName', '$Checksum', '$Mode')
ON DUPLICATE KEY UPDATE checksum = VALUES(checksum), applied_mode = VALUES(applied_mode);
"@
    if ($DryRun) {
        Write-Host "[dry-run] record $FileName as $Mode"
        return
    }
    Invoke-MySqlQuery -Sql $sql -DbName $Database | Out-Null
}

function Test-MigrationRecorded {
    param([string]$FileName)

    $migrationTableExists = Test-Sql "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'schema_migrations') THEN 1 ELSE 0 END;"
    if (-not $migrationTableExists) {
        return $false
    }
    return Test-Sql "SELECT CASE WHEN EXISTS (SELECT 1 FROM schema_migrations WHERE filename = '$FileName') THEN 1 ELSE 0 END;"
}

Get-Command $MysqlPath -ErrorAction Stop | Out-Null
if (-not $SkipBackup) {
    Get-Command $MysqldumpPath -ErrorAction Stop | Out-Null
}

$resolvedMigrationsDir = (Resolve-Path $MigrationsDir).Path
$schemaPath = Join-Path $resolvedMigrationsDir "schema.sql"

$databaseExists = (Invoke-MySqlQuery -Sql "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = '$Database') THEN 1 ELSE 0 END;" | Select-Object -First 1) -eq "1"
if (-not $databaseExists) {
    if (-not $InitIfMissing) {
        throw "Database '$Database' does not exist. Re-run with -InitIfMissing to create it from schema.sql."
    }
    if ($DryRun) {
        Write-Host "[dry-run] create database $Database and import $schemaPath"
        Write-Host "[dry-run] database does not exist yet, so existing-schema migration checks are skipped"
        Write-Host "Database update check complete."
        exit 0
    } else {
        Invoke-MySqlQuery -Sql "CREATE DATABASE IF NOT EXISTS $Database DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" | Out-Null
        Invoke-MySqlFile -FilePath $schemaPath
    }
}

$migrationTableSql = @"
CREATE TABLE IF NOT EXISTS schema_migrations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    filename VARCHAR(200) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    applied_mode VARCHAR(30) NOT NULL DEFAULT 'executed',
    applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_schema_migrations_filename (filename)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='local schema migration history';
"@
if ($DryRun) {
    Write-Host "[dry-run] ensure schema_migrations table"
} else {
    Invoke-MySqlQuery -Sql $migrationTableSql -DbName $Database | Out-Null
}

$migrations = @(
    @{ File = "migration_subject_ideology_split.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'subject_knowledge') AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'ideology_knowledge') THEN 1 ELSE 0 END;" },
    @{ File = "migration_selection_explain_records.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'selection_explain_records') THEN 1 ELSE 0 END;" },
    @{ File = "migration_light_rag_chunks.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'knowledge_chunks') THEN 1 ELSE 0 END;" },
    @{ File = "migration_teaching_materials.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'teaching_materials') THEN 1 ELSE 0 END;" },
    @{ File = "migration_parse_tasks_longtext.sql"; Check = "SELECT CASE WHEN (SELECT data_type FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'parse_tasks' AND column_name = 'parsed_content') IN ('longtext','json') AND (SELECT data_type FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'parse_tasks' AND column_name = 'ai_analysis') IN ('longtext','json') THEN 1 ELSE 0 END;" },
    @{ File = "migration_parse_tasks_error_detail.sql"; Check = "SELECT CASE WHEN COALESCE((SELECT character_maximum_length FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'parse_tasks' AND column_name = 'error_message'), 0) >= 1000 THEN 1 ELSE 0 END;" },
    @{ File = "migration_json_type_upgrade.sql"; Check = "SELECT CASE WHEN (SELECT data_type FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'parse_tasks' AND column_name = 'parsed_content') = 'json' AND EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'parse_tasks' AND index_name = 'idx_gen_doc_title') THEN 1 ELSE 0 END;" },
    @{ File = "migration_parse_task_corrections.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'parse_task_corrections') THEN 1 ELSE 0 END;" },
    @{ File = "migration_phase2_projection_tables.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'parse_task_knowledge_points') AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'parse_task_ideology_matches') AND EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'teaching_materials' AND index_name = 'ft_lecture_notes') THEN 1 ELSE 0 END;" },
    @{ File = "migration_material_trace_and_course_binding.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'parse_tasks' AND column_name = 'course_id') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_materials' AND column_name = 'course_id') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_material_traces' AND column_name = 'resource_title') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_material_traces' AND column_name = 'resource_source') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_material_traces' AND column_name = 'resource_source_url') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_material_traces' AND column_name = 'resource_quoted_excerpt') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_material_traces' AND column_name = 'citation_explanation') THEN 1 ELSE 0 END;" },
    @{ File = "migration_teaching_material_trace_resource_columns.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_material_traces' AND column_name = 'resource_title') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_material_traces' AND column_name = 'resource_source') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_material_traces' AND column_name = 'resource_source_url') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_material_traces' AND column_name = 'resource_quoted_excerpt') AND EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'teaching_material_traces' AND column_name = 'citation_explanation') THEN 1 ELSE 0 END;" },
    @{ File = "migration_course_material_rules.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'course_material_rules') THEN 1 ELSE 0 END;" },
    @{ File = "migration_student_activity_events.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'student_activity_events') THEN 1 ELSE 0 END;" },
    @{ File = "migration_student_alert_records.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'student_alert_records') THEN 1 ELSE 0 END;" },
    @{ File = "migration_course_chapters.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'course_chapters') AND EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'teaching_materials' AND index_name = 'idx_tm_chapter') THEN 1 ELSE 0 END;" },
    @{ File = "migration_crawl_sources_review.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'crawl_sources') AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'crawl_run_logs') AND EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'resources' AND index_name = 'idx_resource_review_status') THEN 1 ELSE 0 END;" },
    @{ File = "migration_keyword_tasks.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'keyword_tasks') AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'keyword_task_items') THEN 1 ELSE 0 END;" },
    @{ File = "migration_match_reviews.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'subject_ideology_matches' AND column_name = 'review_status') AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'subject_ideology_match_reviews') THEN 1 ELSE 0 END;" },
    @{ File = "migration_ai_provider_configs.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'ai_provider_configs') AND EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'ai_route_configs') THEN 1 ELSE 0 END;" },
    @{ File = "migration_course_students.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'course_students') THEN 1 ELSE 0 END;" },
    @{ File = "migration_knowledge_change_logs.sql"; Check = "SELECT CASE WHEN EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'knowledge_change_logs') THEN 1 ELSE 0 END;" }
)

if (-not $SkipBackup -and -not $DryRun) {
    New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
    $backupFile = Join-Path $BackupDir ("{0}_{1}.sql" -f $Database, (Get-Date -Format "yyyyMMdd_HHmmss"))
    $dumpArgs = @(
        "--protocol=TCP",
        "--host=$HostName",
        "--port=$Port",
        "--user=$User",
        "--default-character-set=utf8mb4"
    )
    $dumpArgs += @("--single-transaction", "--routines", "--triggers", "--result-file=$backupFile", $Database)
    $result = Invoke-MySqlNative -CommandPath $MysqldumpPath -Arguments $dumpArgs
    if ($result.ExitCode -ne 0) {
        throw "mysqldump failed: $($result.Output -join [Environment]::NewLine)"
    }
    Write-Host "Backup written to $backupFile"
} elseif (-not $SkipBackup) {
    Write-Host "[dry-run] backup database $Database"
}

foreach ($migration in $migrations) {
    $fileName = $migration.File
    $filePath = Join-Path $resolvedMigrationsDir $fileName
    if (-not (Test-Path $filePath)) {
        throw "Migration file not found: $filePath"
    }

    $checksum = Get-FileChecksum -FilePath $filePath
    if (Test-MigrationRecorded -FileName $fileName) {
        Write-Host "[skip] $fileName already recorded"
        continue
    }

    if (Test-Sql -Sql $migration.Check) {
        Write-Host "[adopt] $fileName already reflected in schema"
        Set-MigrationApplied -FileName $fileName -Checksum $checksum -Mode "adopted"
        continue
    }

    if ($DryRun) {
        Write-Host "[dry-run] execute $fileName"
    } else {
        Write-Host "[execute] $fileName"
        Invoke-MySqlFile -FilePath $filePath
        Set-MigrationApplied -FileName $fileName -Checksum $checksum -Mode "executed"
    }
}

Write-Host "Database update check complete."
