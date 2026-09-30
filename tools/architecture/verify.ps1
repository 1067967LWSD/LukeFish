param(
    [string]$PresentationPath,
    [Parameter(Mandatory = $true)]
    [string]$PreviewDirectory
)

$ErrorActionPreference = 'Stop'
if (-not $PresentationPath) {
    $PresentationPath = Join-Path $PSScriptRoot '..\..\docs\LukeFish-Architecture.pptx'
}
$PresentationPath = (Resolve-Path -LiteralPath $PresentationPath).Path
$PreviewDirectory = [System.IO.Path]::GetFullPath($PreviewDirectory)
New-Item -ItemType Directory -Path $PreviewDirectory -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($PresentationPath)
try {
    $slides = @($archive.Entries | Where-Object FullName -Match '^ppt/slides/slide\d+\.xml$')
    $notes = @($archive.Entries | Where-Object FullName -Match '^ppt/notesSlides/notesSlide\d+\.xml$')
    if ($slides.Count -ne 14 -or $notes.Count -ne 14) {
        throw "Expected 14 slides with notes; found $($slides.Count) slides and $($notes.Count) notes."
    }
    foreach ($entry in @($slides) + @($notes)) {
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { [xml]$document = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if (-not $document.DocumentElement) { throw "Empty presentation part: $($entry.FullName)" }
    }
} finally {
    $archive.Dispose()
}

$existingProcesses = @(Get-Process -Name POWERPNT -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
$application = $null
$presentation = $null
$createdInstance = $false
$problems = [System.Collections.Generic.List[string]]::new()
try {
    $application = New-Object -ComObject PowerPoint.Application
    $createdInstance = $existingProcesses.Count -eq 0
    $presentation = $application.Presentations.Open($PresentationPath, -1, 0, 0)
    if ($presentation.Slides.Count -ne 14) { throw 'PowerPoint did not load all 14 slides.' }
    $width = $presentation.PageSetup.SlideWidth
    $height = $presentation.PageSetup.SlideHeight
    foreach ($slide in $presentation.Slides) {
        foreach ($shape in $slide.Shapes) {
            if ($shape.Left -lt -0.5 -or $shape.Top -lt -0.5 -or $shape.Left + $shape.Width -gt $width + 0.5 `
                    -or $shape.Top + $shape.Height -gt $height + 0.5) {
                $problems.Add("Slide $($slide.SlideIndex): shape outside slide: $($shape.Name)")
            }
            if ($shape.HasTextFrame -eq -1 -and $shape.TextFrame.HasText -eq -1) {
                $frame = $shape.TextFrame2
                $availableHeight = $shape.Height - $frame.MarginTop - $frame.MarginBottom
                $availableWidth = $shape.Width - $frame.MarginLeft - $frame.MarginRight
                $text = $shape.TextFrame.TextRange.Text.Replace("`r", ' | ').Replace("`n", ' ')
                if ($frame.TextRange.BoundHeight -gt $availableHeight + 1.5 `
                        -or $frame.TextRange.BoundWidth -gt $availableWidth + 1.5) {
                    $problems.Add(("Slide {0}: text overflow ({1:N1}x{2:N1} pt in {3:N1}x{4:N1} pt): {5}" -f `
                        $slide.SlideIndex, $frame.TextRange.BoundWidth, $frame.TextRange.BoundHeight, `
                        $availableWidth, $availableHeight, $text))
                }
            }
        }
        $filename = Join-Path $PreviewDirectory ("slide-{0:D2}.png" -f $slide.SlideIndex)
        $slide.Export($filename, 'PNG', 1920, 1080)
    }
    if ($problems.Count) { throw ($problems -join "`n") }
    Write-Host "PowerPoint opened and rendered all 14 slides with no out-of-bounds shapes or overflowing text."
    Write-Host "Previews: $PreviewDirectory"
} finally {
    if ($presentation) {
        $presentation.Close()
        [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($presentation)
    }
    if ($application) {
        # Do not quit a pre-existing instance or close another presentation opened during verification.
        if ($createdInstance -and $application.Presentations.Count -eq 0) { $application.Quit() }
        [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($application)
    }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
