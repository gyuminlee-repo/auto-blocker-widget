#!/usr/bin/env bash
# Renders README preview PNGs of the home screen widget in its four states
# (on / off / unknown / setup needed), in the Color style and the Monochrome style (bg_mono_<state>,
# with dark text and icon on the light On background, as in AutoBlockerWidget.build()),
# by reproducing res/layout/widget.xml as HTML and taking
# headless Chrome screenshots. Colors, radius, icon paths and strings are parsed
# from the resource XML at run time, so the images follow resource changes.
#
# Assumptions (not taken from resources):
#   - widget size 180x76 dp (a 2x1 cell; real size depends on the launcher grid)
#   - 3x1 widget (widget_wide.png, res/layout/widget_wide.xml) is 270x76 dp, 1.5x the 2x1 width
#   - 1 dp = 1 sp = 1 CSS px, rendered at device scale factor 3
#   - example check time "14:32" for on/off (build() hides it for unknown and setup)
#   - system font stack instead of the device font (Roboto / Samsung One UI)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/docs"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
SCALE=3

D="$(mktemp -d)"
cleanup() { rm -f "$D"/*; rmdir "$D"; }
trap cleanup EXIT

python3 - "$ROOT" "$D" <<'PY'
import base64, re, sys, xml.etree.ElementTree as ET
root, out = sys.argv[1], sys.argv[2]
A = '{http://schemas.android.com/apk/res/android}'

def color(c):
    # Android #AARRGGBB -> CSS rgba(); alpha is the FIRST byte
    c = c.lstrip('#')
    if len(c) == 6:
        c = 'FF' + c
    a, r, g, b = (int(c[i:i + 2], 16) for i in (0, 2, 4, 6))
    return f'rgba({r},{g},{b},{a / 255:.3f})'

def dp(v):
    return float(re.match(r'([\d.]+)(dp|sp)$', v).group(1))

strings = {e.get('name'): e.text for e in ET.parse(f'{root}/res/values/strings.xml').getroot()}
dimens = {e.get('name'): e.text for e in ET.parse(f'{root}/res/values/dimens.xml').getroot()}
colors = {e.get('name'): e.text for e in ET.parse(f'{root}/res/values/colors.xml').getroot()}

def bg(name):
    shape = ET.parse(f'{root}/res/drawable/{name}.xml').getroot()
    solid = shape.find('solid').get(A + 'color')
    rad = shape.find('corners').get(A + 'radius')
    if rad.startswith('@dimen/'):
        rad = dimens[rad[len('@dimen/'):]]
    return color(solid), dp(rad)

def icon(name, tint):
    # tint mirrors ImageView.setColorFilter(fg) (SRC_ATOP): every opaque path takes the fg color
    v = ET.parse(f'{root}/res/drawable/{name}.xml').getroot()
    vw, vh = v.get(A + 'viewportWidth'), v.get(A + 'viewportHeight')
    paths = []
    for p in v.findall('path'):
        rule = 'evenodd' if p.get(A + 'fillType') == 'evenOdd' else 'nonzero'
        paths.append(f'<path fill="{tint}" fill-rule="{rule}" d="{p.get(A + "pathData")}"/>')
    return f'<svg viewBox="0 0 {vw} {vh}" width="100%" height="100%">{"".join(paths)}</svg>'

# Layout values from res/layout/widget.xml
lay = ET.parse(f'{root}/res/layout/widget.xml').getroot()
ids = {}
for e in lay.iter():
    i = e.get(A + 'id')
    if i:
        ids[i.split('/')[-1]] = e
lay = ids['content']  # root is a FrameLayout (bg layer + content); padding lives on content
textcol = lay.findall('LinearLayout')[0]
L = dict(
    ps=dp(lay.get(A + 'paddingStart')), pe=dp(lay.get(A + 'paddingEnd')),
    pt=dp(lay.get(A + 'paddingTop')), pb=dp(lay.get(A + 'paddingBottom')),
    icon=dp(ids['icon'].get(A + 'layout_width')),
    textms=dp(textcol.get(A + 'layout_marginStart')),
    c_sz=dp(ids['checked_at'].get(A + 'textSize')), c_col=color(ids['checked_at'].get(A + 'textColor')),
    c_ms=dp(ids['checked_at'].get(A + 'layout_marginStart')),
    l_sz=dp(ids['label'].get(A + 'textSize')), l_col=color(ids['label'].get(A + 'textColor')),
    l_w='700' if ids['label'].get(A + 'textStyle') == 'bold' else '400',
)

# 3x1 extras from res/layout/widget_wide.xml: divider + Play Protect shortcut
wl = ET.parse(f'{root}/res/layout/widget_wide.xml').getroot()
wids = {e.get(A + 'id').split('/')[-1]: e for e in wl.iter() if e.get(A + 'id')}
P = dict(
    d_w=dp(wids['pp_divider'].get(A + 'layout_width')), d_ms=dp(wids['pp_divider'].get(A + 'layout_marginStart')),
    d_mt=dp(wids['pp_divider'].get(A + 'layout_marginTop')), d_mb=dp(wids['pp_divider'].get(A + 'layout_marginBottom')),
    ps=dp(wids['play_protect'].get(A + 'paddingStart')), pe=dp(wids['play_protect'].get(A + 'paddingEnd')),
    icon=dp(wids['pp_icon'].get(A + 'layout_width')),
    l_sz=dp(wids['pp_label'].get(A + 'textSize')), l_mt=dp(wids['pp_label'].get(A + 'layout_marginTop')),
)
PP_LABEL = strings[wids['pp_label'].get(A + 'text').split('/')[-1]]

W, H, GAP = 180, 76, 16
WW = 270  # 3x1 width (assumption, see header)
ROW_GAP = 12  # space between the Color row and the Monochrome row
# Header row above the 2x1 strip only: app icon (docs/media/icon.png, inlined) + app name
HEAD, HEAD_MB = 24, 10
APP = strings['app_name']
ICON_URI = 'data:image/png;base64,' + base64.b64encode(open(f'{root}/docs/media/icon.png', 'rb').read()).decode()
CHECK = strings['checked_at'].replace('%1$s', '14:32')
# State -> resources, mirroring AutoBlockerWidget.build(): on/off show checked_at, unknown and setup hide it.
# Setup (accessibility service off) reuses the unknown backgrounds with its own icon and label.
STATES = [
    ('on', 'On', strings['state_on'], 'bg_on', 'bg_mono_on', 'ic_shield_on', True),
    ('off', 'Off', strings['state_off'], 'bg_off', 'bg_mono_off', 'ic_shield_off', True),
    ('unknown', 'Unknown', strings['state_unknown'], 'bg_unknown', 'bg_mono_unknown', 'ic_shield_unknown', False),
    ('setup', 'Setup needed', strings['widget_state_setup'], 'bg_unknown', 'bg_mono_unknown', 'ic_shield_setup', False),
]
N = len(STATES)

CSS = f'''
html,body{{margin:0;padding:0;background:transparent;}}
*{{box-sizing:border-box;}}
body{{font-family:"Apple SD Gothic Neo","Noto Sans KR",sans-serif;-webkit-font-smoothing:antialiased;}}
.w{{width:{W}px;height:{H}px;display:flex;flex-direction:row;align-items:center;
  padding:{L["pt"]}px {L["pe"]}px {L["pb"]}px {L["ps"]}px;overflow:hidden;}}
.ic{{width:{L["icon"]}px;height:{L["icon"]}px;flex:none;}}
.ic svg{{display:block;}}
.tx{{flex:1 1 0;min-width:0;margin-left:{L["textms"]}px;display:flex;flex-direction:column;}}
.row{{display:flex;flex-direction:row;align-items:baseline;min-width:0;}}
.at{{flex:none;white-space:nowrap;margin-left:{L["c_ms"]}px;font-size:{L["c_sz"]}px;color:{L["c_col"]};}}
.label{{flex:0 1 auto;min-width:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;
  font-size:{L["l_sz"]}px;font-weight:{L["l_w"]};color:{L["l_col"]};}}
.sheet{{display:flex;flex-direction:column;align-items:center;}}
.head{{display:flex;flex-direction:row;align-items:center;height:{HEAD}px;margin-bottom:{HEAD_MB}px;}}
.head img{{display:block;width:{HEAD}px;height:{HEAD}px;border-radius:{HEAD // 4}px;}}
.head span{{margin-left:8px;font-size:16px;line-height:{HEAD}px;font-weight:700;color:#374151;}}
.strip{{display:flex;flex-direction:row;gap:{GAP}px;}}
.strip+.strip{{margin-top:{ROW_GAP}px;}}
.cell{{display:flex;flex-direction:column;align-items:center;}}
.cap{{margin-top:6px;font-size:12px;line-height:16px;color:#6B7280;}}
.dv{{flex:none;align-self:stretch;width:{P["d_w"]}px;margin:{P["d_mt"]}px 0 {P["d_mb"]}px {P["d_ms"]}px;}}
.pp{{flex:none;align-self:stretch;display:flex;flex-direction:column;align-items:center;justify-content:center;
  padding:0 {P["pe"]}px 0 {P["ps"]}px;}}
.pp .pi{{width:{P["icon"]}px;height:{P["icon"]}px;}}
.pp .pi svg{{display:block;}}
.pp .pl{{margin-top:{P["l_mt"]}px;font-size:{P["l_sz"]}px;white-space:nowrap;}}
'''

# Text and icon colors, mirroring AutoBlockerWidget.build(): dark only for Monochrome On
FG = {False: (color(colors['widget_fg_light']), color(colors['widget_fg_light_sub'])),
      True: (color(colors['widget_fg_dark']), color(colors['widget_fg_dark_sub']))}

def widget(label, bgname, iconname, show_at, dark=False, wide=False):
    c, r = bg(bgname)
    fg, sub = FG[dark]
    at = f'<div class="at" style="color:{sub}">{CHECK}</div>' if show_at else ''
    # 3x1: pp_icon and pp_label take fg, pp_divider takes fgSub (AutoBlockerWidget.build())
    pp = (f'<div class="dv" style="background:{sub}"></div><div class="pp">'
          f'<div class="pi">{icon("ic_play_protect", fg)}</div>'
          f'<div class="pl" style="color:{fg}">{PP_LABEL}</div></div>') if wide else ''
    size = f'width:{WW}px;' if wide else ''
    return (f'<div class="w" style="{size}background:{c};border-radius:{r}px">'
            f'<div class="ic">{icon(iconname, fg)}</div>'
            f'<div class="tx"><div class="row"><div class="label" style="color:{fg}">{label}</div>{at}</div></div>{pp}</div>')

def page(body):
    return f'<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head><body>{body}</body></html>'

for key, cap, label, b, mb, i, show in STATES:
    open(f'{out}/{key}.html', 'w').write(page(widget(label, b, i, show)))
def strip(style, mono, wide=False):
    cells = ''.join(f'<div class="cell">{widget(l, mb if mono else b, i, s, mono and k == "on", wide)}'
                    f'<div class="cap">{style} \u00b7 {cap}</div></div>'
                    for k, cap, l, b, mb, i, s in STATES)
    return f'<div class="strip">{cells}</div>'
head = f'<div class="head"><img src="{ICON_URI}" alt=""><span>{APP}</span></div>'
open(f'{out}/states.html', 'w').write(page(f'<div class="sheet">{head}{strip("Color", False)}{strip("Monochrome", True)}</div>'))
open(f'{out}/wide.html', 'w').write(page(f'<div class="sheet">{strip("Color", False, True)}{strip("Monochrome", True, True)}</div>'))
open(f'{out}/sizes', 'w').write(f'{W},{H} {N * W + (N - 1) * GAP},{HEAD + HEAD_MB + 2 * (H + 6 + 16) + ROW_GAP} '
                                f'{N * WW + (N - 1) * GAP},{2 * (H + 6 + 16) + ROW_GAP}\n')
PY

read -r SINGLE STRIP WIDE < "$D/sizes"

shot() { # html png w,h  (perl alarm = 30 s hard timeout; headless Chrome can hang)
  rm -f "$2"
  perl -e 'alarm 30; exec @ARGV' "$CHROME" --headless=new --disable-gpu --hide-scrollbars \
    --force-device-scale-factor="$SCALE" --default-background-color=00000000 \
    --window-size="$3" --screenshot="$2" "file://$1" >/dev/null 2>&1 \
    && [ -s "$2" ] || { echo "screenshot failed: $2" >&2; return 1; }
}

for s in on off unknown setup; do
  shot "$D/$s.html" "$OUT/widget_$s.png" "$SINGLE"
done
shot "$D/states.html" "$OUT/widget_states.png" "$STRIP"
shot "$D/wide.html" "$OUT/widget_wide.png" "$WIDE"
echo "wrote: widget_on.png widget_off.png widget_unknown.png widget_setup.png widget_states.png widget_wide.png in $OUT"
