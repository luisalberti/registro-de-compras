# =====================================================================
#  Sube la app Boletas a GitHub, sola.
#
#  Como usarlo:
#    1. Descomprime el ZIP de la app.
#    2. Copia ESTE archivo (subir.ps1) DENTRO de esa carpeta, al lado de
#       "settings.gradle.kts".
#    3. Clic derecho sobre subir.ps1  ->  "Ejecutar con PowerShell".
#
#  La primera vez, git te va a abrir el navegador para que entres a tu
#  cuenta de GitHub. Es normal: es como le das permiso para subir.
# =====================================================================

$ErrorActionPreference = "Stop"
$repo = "https://github.com/luisalberti/registro-de-compras.git"

function Fin($msg, $color = "White") { Write-Host ""; Write-Host $msg -ForegroundColor $color }

# Trabaja en la carpeta donde esta este script.
Set-Location -Path $PSScriptRoot

# --- Comprobacion: el script esta en la carpeta correcta ---------------
if (-not (Test-Path "settings.gradle.kts")) {
    Fin "NO encuentro settings.gradle.kts aqui." "Red"
    Write-Host "Este script tiene que estar DENTRO de la carpeta de la app,"
    Write-Host "al lado de settings.gradle.kts, build.gradle.kts y la carpeta app."
    Write-Host "Muevelo ahi y vuelve a ejecutarlo."
    Read-Host "`nEnter para cerrar"; exit 1
}

# --- Comprobacion: git instalado ---------------------------------------
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Fin "Git no esta instalado. Lo instalo..." "Yellow"
    try {
        winget install --id Git.Git -e --source winget --accept-package-agreements --accept-source-agreements
        Fin "Git instalado. CIERRA esta ventana, abre la carpeta de nuevo y" "Green"
        Write-Host "vuelve a ejecutar subir.ps1 (git recien queda disponible al reabrir)."
    } catch {
        Fin "No pude instalar git solo." "Red"
        Write-Host "Instalalo a mano desde https://git-scm.com/download/win y reintenta."
    }
    Read-Host "`nEnter para cerrar"; exit 1
}

Write-Host "Preparando la subida..." -ForegroundColor Cyan

# --- Identidad de git (obligatoria para poder confirmar) ---------------
if (-not (git config user.email)) { git config user.email "luisalberto.fernandezn@gmail.com" }
if (-not (git config user.name))  { git config user.name  "luisalberti" }

# --- Inicializa el repositorio si hace falta ---------------------------
if (-not (Test-Path ".git")) { git init | Out-Null }
git branch -M main

# --- No subir este propio script ---------------------------------------
if (-not (Test-Path ".gitignore")) { New-Item .gitignore -ItemType File | Out-Null }
if (-not (Select-String -Path .gitignore -Pattern "subir.ps1" -Quiet)) {
    Add-Content .gitignore "`nsubir.ps1"
}

# --- Agrega todo y confirma --------------------------------------------
git add -A
try {
    git commit -m "App Boletas: escanear documentos y exportar a Excel" | Out-Null
    Write-Host "Archivos preparados." -ForegroundColor Green
} catch {
    Write-Host "No habia cambios nuevos que confirmar (quizas ya lo subiste)." -ForegroundColor Yellow
}

# --- Conecta con el repo de GitHub -------------------------------------
if (git remote | Select-String -Pattern "^origin$" -Quiet) {
    git remote set-url origin $repo
} else {
    git remote add origin $repo
}

# --- Sube --------------------------------------------------------------
Fin "Subiendo a GitHub. Si se abre el navegador, entra a tu cuenta." "Cyan"
git push -u origin main

if ($LASTEXITCODE -eq 0) {
    Fin "LISTO. Todo subido." "Green"
    Write-Host "Ahora ve al repositorio en GitHub, pestana 'Actions':"
    Write-Host "  https://github.com/luisalberti/registro-de-compras/actions"
    Write-Host "Cuando el circulo quede verde, el .apk esta en 'Releases'."
} else {
    Fin "Algo fallo al subir." "Red"
    Write-Host "Lo mas comun: cerraste la ventana del navegador sin entrar,"
    Write-Host "o el repositorio ya tenia archivos. Copia el texto rojo de arriba"
    Write-Host "y mandalo para revisarlo."
}

Read-Host "`nEnter para cerrar"
