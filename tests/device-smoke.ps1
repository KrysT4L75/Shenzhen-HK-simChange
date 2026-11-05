# Run only against the dedicated virtual device created for this project.
param([ValidateSet('pause','settings','stop')][string]$Stage='pause')
$ErrorActionPreference='Stop'
$script:QaAdb='C:\Users\Administrator\AppData\Local\Temp\GuoguanHuankaQA\platform-tools\adb.exe'
Set-Location (Split-Path $PSScriptRoot -Parent)
function Invoke-Device { & $script:QaAdb -P 5038 -s emulator-5556 @args }
function Get-TestUi {
    Invoke-Device shell uiautomator dump /sdcard/guard-qa.xml | Out-Null
    Invoke-Device pull /sdcard/guard-qa.xml build/qa-ui.xml 2>$null | Out-Null
    [xml]$tree=Get-Content -LiteralPath build/qa-ui.xml
    return $tree
}
function Click-Label([string]$Label,[int]$Scrolls=0) {
    for($attempt=0;$attempt -le $Scrolls;$attempt++) {
        $tree=Get-TestUi
        $node=$tree.SelectNodes('//node') | Where-Object {$_.text -eq $Label} | Select-Object -First 1
        if($node) {
            $coords=[regex]::Matches($node.bounds,'\d+') | ForEach-Object {[int]$_.Value}
            if($coords[3]-$coords[1] -ge 30) {
                Invoke-Device shell input tap ([int](($coords[0]+$coords[2])/2)) ([int](($coords[1]+$coords[3])/2))
                Write-Output ('Clicked: '+$Label)
                return
            }
        }
        if($attempt -lt $Scrolls){Invoke-Device shell input swipe 550 1950 550 700 350}
    }
    throw ('UI label missing: '+$Label)
}
if($Stage -eq 'pause') {
    Invoke-Device shell cmd statusbar expand-notifications
    Click-Label '暂停提醒10分钟'
    Invoke-Device shell cmd statusbar collapse
    Invoke-Device shell am start -W -n cn.shenlink.guard/.MainActivity | Out-Null
    $tree=Get-TestUi
    $tree.SelectNodes('//node') | Where-Object {$_.text -match '暂停|守护'} | Select-Object text | ConvertTo-Json -Compress
}
if($Stage -eq 'settings') {
    Invoke-Device shell am start -W -n cn.shenlink.guard/.MainActivity | Out-Null
    Click-Label '在内地用  ›' 3
    $tree=Get-TestUi
    $choice=$tree.SelectNodes('//node') | Where-Object {$_.text -like '卡 1*'} | Select-Object -First 1
    if(!$choice){throw 'No test SIM option'}
    Click-Label $choice.text
    Click-Label '在香港用  ›' 3
    $tree=Get-TestUi
    $choice=$tree.SelectNodes('//node') | Where-Object {$_.text -like '卡 1*'} | Select-Object -First 1
    Click-Label $choice.text
    Click-Label '提醒范围  ›' 3
    Click-Label '2 公里 · 更早提醒'
    Click-Label '这次的方向  ›' 2
    Click-Label '香港 → 深圳'
    Click-Label '发送一条测试提醒' 4
    Invoke-Device shell dumpsys notification --noredact | Select-String 'NotificationRecord.*cn.shenlink.guard|android.title=String \(测试提醒'
    Click-Label 'ColorOS 后台设置指南' 1
    $tree=Get-TestUi
    if(!($tree.SelectNodes('//node') | Where-Object {$_.text -eq 'OPPO / ColorOS 后台运行'})){throw 'Missing ColorOS help'}
    Click-Label '知道了'
    Click-Label '查看最近提醒' 3
    $tree=Get-TestUi
    $tree.SelectNodes('//node') | Where-Object {$_.text -match '最近提醒|发送测试|快到罗湖'} | Select-Object text | ConvertTo-Json -Compress
    Click-Label '关闭'
    Invoke-Device shell screencap -p /sdcard/guard-settings.png
    Invoke-Device pull /sdcard/guard-settings.png dist/settings.png
}
if($Stage -eq 'stop') {
    Invoke-Device shell am force-stop cn.shenlink.guard
    Invoke-Device shell am start -W -n cn.shenlink.guard/.MainActivity | Out-Null
    $tree=Get-TestUi
    if(!($tree.SelectNodes('//node') | Where-Object {$_.text -eq '守护已中断'})){throw 'Interrupted service not reflected in UI'}
    Write-Output 'PASS: force stop is shown as interrupted, never running'
    Click-Label '开启通勤守护'
    $tree=Get-TestUi
    if(!($tree.SelectNodes('//node') | Where-Object {$_.text -eq '通勤守护中'})){throw 'Failed to resume service'}
    Click-Label '停止通勤守护'
    $tree=Get-TestUi
    if(!($tree.SelectNodes('//node') | Where-Object {$_.text -eq '守护未开启'})){throw 'Stop not reflected'}
    $service=Invoke-Device shell dumpsys activity services cn.shenlink.guard
    if($service -match 'isForeground=true'){throw 'Service still running after stop'}
    Write-Output 'PASS: user stop removes foreground service'
}
Invoke-Device logcat -d -t 500 -s AndroidRuntime:E
