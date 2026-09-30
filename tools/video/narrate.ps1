param(
    [Parameter(Mandatory = $true)]
    [string]$InputFile,
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,
    [string]$Voice = 'Microsoft David Desktop',
    [ValidateRange(-10, 10)]
    [int]$Rate = 1
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
Add-Type -ReferencedAssemblies System.Speech -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Speech.AudioFormat;
using System.Speech.Synthesis;

public sealed class NarrationWord {
    public int Start;
    public int Length;
    public double Seconds;
}

public static class LocalNarrator {
    public static NarrationWord[] Write(string text, string path, string voice, int rate) {
        var words = new List<NarrationWord>();
        using (var speaker = new SpeechSynthesizer()) {
            speaker.SelectVoice(voice);
            speaker.Rate = rate;
            speaker.Volume = 100;
            // Match the desktop voice's 16 kHz word-event clock; resample only during encoding.
            speaker.SetOutputToWaveFile(path,
                new SpeechAudioFormatInfo(16000, AudioBitsPerSample.Sixteen, AudioChannel.Mono));
            speaker.SpeakProgress += (sender, args) => words.Add(new NarrationWord {
                Start = args.CharacterPosition,
                Length = args.CharacterCount,
                Seconds = args.AudioPosition.TotalSeconds
            });
            speaker.Speak(text);
            speaker.SetOutputToNull();
        }
        if (words.Count == 0) {
            throw new InvalidOperationException("The speech voice did not report word timings.");
        }
        return words.ToArray();
    }
}
'@

$inputItems = Get-Content -LiteralPath $InputFile -Raw -Encoding UTF8 | ConvertFrom-Json
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)
foreach ($item in $inputItems) {
    if ($item.id -notmatch '^[a-z0-9-]+$') {
        throw "Invalid narration identifier: $($item.id)"
    }
    $wav = Join-Path $OutputDirectory ($item.id + '.wav')
    $timing = Join-Path $OutputDirectory ($item.id + '.words.json')
    $words = [LocalNarrator]::Write($item.text, $wav, $Voice, $Rate)
    $json = ConvertTo-Json -InputObject @($words) -Depth 4
    [System.IO.File]::WriteAllText($timing, $json, $utf8)
    Write-Host "Narrated $($item.id)"
}
