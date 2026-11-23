param(
    [ValidateSet('ftb', 'ours')][string]$Target = 'ftb',
    [string]$Steps = 'info',
    [string]$Out = (Join-Path $PSScriptRoot 'shots')
)
Add-Type -AssemblyName System.Drawing, System.Windows.Forms
Add-Type @"
using System; using System.Text; using System.Runtime.InteropServices; using System.Collections.Generic;
public static class W {
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc p, IntPtr l);
  [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr dc, uint flags);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, int d, UIntPtr e);
  [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint a, uint b, bool f);
  [DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, uint f, UIntPtr e);
  [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  public static List<IntPtr[]> Find(uint[] pids) {
    var res = new List<IntPtr[]>();
    EnumWindows((h, l) => { uint p; GetWindowThreadProcessId(h, out p);
      if (IsWindowVisible(h) && Array.IndexOf(pids, p) >= 0) { var sb = new StringBuilder(256); GetWindowText(h, sb, 256);
        if (sb.Length > 0) res.Add(new IntPtr[]{ h, (IntPtr)p }); } return true; }, IntPtr.Zero);
    return res;
  }
}
"@

function Get-GameWindow {
    $proc = if ($Target -eq 'ftb') { 'javaw' } else { 'java' }
    $pids = [uint32[]]@(Get-Process $proc -ErrorAction SilentlyContinue | ForEach-Object { $_.Id })
    foreach ($pair in [W]::Find($pids)) {
        $sb = New-Object System.Text.StringBuilder 256
        [void][W]::GetWindowText($pair[0], $sb, 256)
        $t = $sb.ToString()
        if (($Target -eq 'ftb' -and $t -like 'Minecraft*26.1.2*') -or ($Target -eq 'ours' -and $t -eq 'Cleanroom')) { return $pair[0] }
    }
    return [IntPtr]::Zero
}

$h = Get-GameWindow
if ($h -eq [IntPtr]::Zero) { Write-Output "NO WINDOW for target '$Target' (is the game running?)"; exit 2 }
New-Item -ItemType Directory -Force $Out | Out-Null

function Get-Rect { $r = New-Object W+RECT; [void][W]::GetWindowRect($h, [ref]$r); return $r }

function Set-Front {
    for ($i = 0; $i -lt 5; $i++) {
        if ([W]::GetForegroundWindow() -eq $h) { return $true }
        [void][W]::ShowWindow($h, 3)
        $fg = [W]::GetForegroundWindow(); $p = 0
        $a = [W]::GetWindowThreadProcessId($fg, [ref]$p); $me = [W]::GetCurrentThreadId()
        [void][W]::AttachThreadInput($me, $a, $true)
        [W]::keybd_event(0x12, 0, 0, [UIntPtr]::Zero); [W]::keybd_event(0x12, 0, 2, [UIntPtr]::Zero)
        [void][W]::SetForegroundWindow($h)
        [void][W]::AttachThreadInput($me, $a, $false)
        Start-Sleep -Milliseconds 250
    }
    return ([W]::GetForegroundWindow() -eq $h)
}

function Save-Shot($name) {
    $r = Get-Rect; $w = $r.R - $r.L; $ht = $r.B - $r.T
    $bmp = New-Object System.Drawing.Bitmap $w, $ht
    $g = [System.Drawing.Graphics]::FromImage($bmp); $dc = $g.GetHdc()
    [void][W]::PrintWindow($h, $dc, 2); $g.ReleaseHdc($dc); $g.Dispose()
    $path = Join-Path $Out "$name.png"; $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png); $bmp.Dispose()
    Write-Output "shot $path ${w}x$ht"
}

function Move-To($x, $y) { $r = Get-Rect; [void][W]::SetCursorPos($r.L + [int]$x, $r.T + [int]$y); Start-Sleep -Milliseconds 60 }
function Click-At($x, $y, $right = $false) {
    if (-not (Set-Front)) { Write-Output "FOCUS FAILED"; return }
    Move-To $x $y
    $d = if ($right) { 8 } else { 2 }; $u = if ($right) { 16 } else { 4 }
    [W]::mouse_event($d, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 70
    [W]::mouse_event($u, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 120
}

foreach ($raw in $Steps -split ';') {
    $s = $raw.Trim(); if (-not $s) { continue }
    $p = $s -split '\s+', 2; $cmd = $p[0].ToLower(); $arg = if ($p.Count -gt 1) { $p[1] } else { '' }
    $n = @($arg -split '\s+' | Where-Object { $_ -ne '' })
    switch ($cmd) {
        'info'     { $r = Get-Rect; Write-Output "window $($r.L),$($r.T) $($r.R - $r.L)x$($r.B - $r.T) foreground=$([W]::GetForegroundWindow() -eq $h)" }
        'focus'    { Write-Output "focus=$(Set-Front)" }
        'shot'     { Save-Shot $arg }
        'wait'     { Start-Sleep -Milliseconds ([int]$arg) }
        'move'     { [void](Set-Front); Move-To $n[0] $n[1] }
        'click'    { Click-At $n[0] $n[1] }
        'rclick'   { Click-At $n[0] $n[1] $true }
        'dblclick' { Click-At $n[0] $n[1]; Click-At $n[0] $n[1] }
        'drag'     {
            if (Set-Front) { Move-To $n[0] $n[1]; [W]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 120
                $steps = 12
                for ($i = 1; $i -le $steps; $i++) { Move-To ([int]$n[0] + ([int]$n[2] - [int]$n[0]) * $i / $steps) ([int]$n[1] + ([int]$n[3] - [int]$n[1]) * $i / $steps); Start-Sleep -Milliseconds 30 }
                [W]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 150 }
        }
        'wheel'    { if (Set-Front) { Move-To $n[0] $n[1]; [W]::mouse_event(0x800, 0, 0, [int]([int]$n[2] * 120), [UIntPtr]::Zero); Start-Sleep -Milliseconds 150 } }
        'key'      { if (Set-Front) { [System.Windows.Forms.SendKeys]::SendWait($arg); Start-Sleep -Milliseconds 150 } }
        'type'     { if (Set-Front) { foreach ($c in $arg.ToCharArray()) { [System.Windows.Forms.SendKeys]::SendWait($(if ('+^%~(){}[]'.Contains($c)) { "{$c}" } else { "$c" })); Start-Sleep -Milliseconds 40 } } }
        default    { Write-Output "unknown step: $s" }
    }
}
