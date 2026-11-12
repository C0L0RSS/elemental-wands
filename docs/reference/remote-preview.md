# Viewing local previews from another device

The developer often drives this repository from a Mac or a phone through T3 Code,
connected to the Windows PC that holds the checkout (`Anton_pc`). Local review servers,
such as the Pyre King workshop (`art/pyre_king/workshop/serve.py`, port 8410), listen only
on the PC's `127.0.0.1`. They are viewed in a preview tab that runs in T3's browser on the
PC (`runtime: server`) and streams to the remote device.

## Symptom

From the remote device, a PC-side preview tab shows **Connecting…** forever. The page
still loads on the PC and its scripts run (`preview_evaluate` works), but
`preview_snapshot` times out after 15 s. A tab opened in the remote device's own browser
pane fails with `ERR_CONNECTION_REFUSED`, which is expected: there, `127.0.0.1` is the
remote device.

## Cause (confirmed November 7, 2026)

A PC-side preview tab is a guest inside T3's window on the PC. T3 keeps it offscreen and
paints it behind the app only while a viewer is streaming or a screenshot is pending.
Every frame therefore needs Chromium to treat T3's PC window as visible.

On Windows, Chromium's native occlusion tracker (`CalculateNativeWinOcclusion`) marks
every window hidden when Windows powers the display off. It does the same when the
session locks. This PC turns the display off after 180 s on AC power (`powercfg`
`VIDEOIDLE` = `0xb4`), so the preview soon stops drawing whenever nobody is at the PC.

| Test, November 7 | Result |
| --- | --- |
| T3 preview tab, display on | Snapshot returned. |
| Same tab, display forced off | Snapshot timed out after 15 s. |
| Same tab, display woken | Snapshot returned again. |
| Chrome, default flags, display off | Page `hidden`; 0 animation frames in 3 s. |
| Chrome with `--disable-features=CalculateNativeWinOcclusion`, display off | Page `visible`; 401 frames in 3 s. |
| Chrome with the flag, window minimized | Page `hidden`; 0 frames. |

A background T3 tab fires no `requestAnimationFrame` callbacks unless a capture is
pending, even with the display on. A silent `requestAnimationFrame` alone does not show
this fault. The failure to check is a snapshot timing out.

Versions: T3 Code Nightly `0.1.8-nightly.20261106.4275`, Electron 44.10.1 (Chromium 152).

## Fix (applied November 7, 2026)

T3 now starts with `--disable-features=CalculateNativeWinOcclusion`. The flag is set on
both shortcuts:

- `%APPDATA%\Microsoft\Windows\Start Menu\Programs\T3 Code (Nightly).lnk`
- `%APPDATA%\Microsoft\Internet Explorer\Quick Launch\User Pinned\TaskBar\T3 Code (Nightly).lnk`

It takes effect the next time T3 starts from either shortcut. The flag should also survive
auto-updates:

- The installer creates the Start menu shortcut only when it is missing.
- The updater relaunches T3 with `--updated`, and Windows appends that to a shortcut's own
  arguments. Both arguments were passed in a test with `ShellExecute`.

Check that the running T3 has the flag. This prints `True` when it is active:

```powershell
(Get-CimInstance Win32_Process -Filter "Name = 'T3 Code (Nightly).exe'" |
  Where-Object CommandLine -match 'gpu-process').CommandLine -match 'CalculateNativeWinOcclusion'
```

If it prints `False`, quit T3 and start it from the Start menu or the taskbar. Do this
after an update too, until an update has been seen to keep the flag.

Checked in T3 on November 9, after a reboot, with the flag active:

| T3's window on the PC | Preview snapshot |
| --- | --- |
| Minimized | Timed out after 15 s. |
| Restored, fully covered by other windows | Returned. |

Without the flag, Chromium also hides a fully covered window, so this shows the occlusion
tracker is off. The display-off case was not repeated in T3, because someone was using
the PC and their input woke the display at once.

Limits:

- **Do not minimize T3's window on the PC.** A minimized window still stops painting.
  Covered by other windows is fine. To restore it from a remote session without taking
  focus, call `ShowWindowAsync(hwnd, 4)` (`SW_SHOWNOACTIVATE`), then `SetWindowPos` with
  `HWND_BOTTOM`.
- A start that bypasses those shortcuts, such as running the `.exe` directly, omits the
  flag.
- The flag should also cover a locked session, but that case has not been tested.

To undo the fix, remove the flag from the end of each shortcut's **Target** field (in
Properties), then restart T3.

## Testing with the display off

Run this on the PC to force the display off. It is the same as the idle timeout:

```powershell
Add-Type -Name U -Namespace W -MemberDefinition '[DllImport("user32.dll")] public static extern bool PostMessage(System.IntPtr h, uint m, System.IntPtr w, System.IntPtr l);'
[W.U]::PostMessage([IntPtr]0xffff, 0x0112, [IntPtr]0xF170, [IntPtr]2)
```

Then call `preview_snapshot` on a PC-side tab. It should return. Any input, or
`SetThreadExecutionState(ES_DISPLAY_REQUIRED)`, wakes the display.

## Fallbacks

- **Publish the review page as a claude.ai artifact.** The workshop is a static page
  (`index.html`, scripts, and the `candidate/` model files) with no outside libraries. It
  can be published as a private multi-file artifact, and the 3D then runs on the viewing
  device. Republish after each build. The Pyre King workshop lives at
  <https://claude.ai/artifact/AvHSHQB9CgVayueN1Xuizp>.
- **Keep the display on:** `powercfg /change monitor-timeout-ac 0`. This leaves an unlocked
  desktop lit at home.
- **Reach the PC over a private network**, such as Tailscale. The server would have to
  listen on that interface instead of `127.0.0.1`. This is not set up.

## Upstream

T3 could fix this for everyone: append `disable-features=CalculateNativeWinOcclusion` on
Windows at startup, as it already does for a Linux feature, or render preview guests
offscreen.
