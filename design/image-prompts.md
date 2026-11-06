# Rider Intercom — Image-Generation Prompt Set

Seven labelled prompts (Call screen and Logo have three variants each). Every prompt is
self-contained: it repeats the app context, the palette and the dark-theme/high-contrast
rule, so any one block can be pasted on its own. Visual direction is deliberately left to
the generator.

---

## 1. Start screen

```
UI design concept: the start/setup screen of an Android app called "Rider Intercom".

What the app is: a direct rider-to-rider intercom for motorcyclists. Two riders' phones link to each other over Bluetooth — no internet, no phone call — and carry voice while each rider's own music keeps playing. Whoever speaks first takes the channel.

Who looks at this screen: a motorcyclist with the phone mounted on the handlebars or sitting in a tank bag, in daylight, seen through a helmet visor, possibly wearing gloves. So the design must be a utility instrument, not a consumer social app: high contrast, large type, generous touch targets, very few colours, no decorative clutter, nothing small or thin.

Content and hierarchy, top to bottom:
1. App title "Rider Intercom" with a short one-line subtitle underneath.
2. A "Permissions needed" card with a short explanatory line and one prominent primary button.
3. A single-line text field labelled for the rider's own name, with placeholder text.
4. A "Paired phones" card: a short list of already-bonded Bluetooth devices, each row a device name plus a radio-button selector, one row selected.
5. Two large, full-width primary action buttons stacked: "Host · wait for buddy" and "Join · <device name>".
6. A final warning card about this phone's battery manager being able to kill the call mid-ride, with its own button.

Strict colour language — dark theme only, this app is never shown in light mode:
- page background #101418
- card surfaces #1E252C
- accent / primary #4FC3F7 (light blue)
- danger red #EF5350 (available, used sparingly here)
- primary text #E6EDF3
- dimmed secondary text #8A99A6
- rounded cards, roughly 16dp corner radius

Format: a single Android phone screen, portrait, aspect ratio 9:19.5, rendered edge to edge and fully legible. Text should read as real, plausible interface copy rather than lorem ipsum scribble.

Rendering style and overall art direction are up to you — pick a direction you think suits a piece of riding equipment and commit to it completely and consistently. Choose your own type treatment, spacing rhythm, iconography, surface treatment and degree of depth. The only non-negotiables are the palette above, the dark background, the content list, and brutal daylight legibility.

Mood/lighting: calm, pre-ride, equipment-grade. Make it feel like gear you trust before you set off.
```

---

## 2. Call screen — Variant A (instrument / gauge reading)

```
UI design concept: the main in-ride screen of an Android app called "Rider Intercom".

What the app is: a direct rider-to-rider intercom for motorcyclists. Two phones link over Bluetooth — no internet, no phone call — and carry voice while each rider's own music keeps playing. It is voice-activated: whoever speaks first takes the channel and the other rider's mic auto-mutes.

Who looks at this screen: a rider at speed, glancing for well under a second at a bar-mounted phone, in daylight, through a helmet visor, in gloves. The single most important job of this screen is that the state — am I talking, is my buddy talking, is the channel clear — must be readable from the corner of the eye without focusing. Treat it as an instrument in a motorcycle cockpit, not an app.

Content and hierarchy, top to bottom:
1. A large circular status indicator dominating the upper screen, containing a microphone glyph and a short state label. Show the state "You are talking" in green #66BB6A. (The same dial also serves amber #FFB74D "<Buddy> is talking" with a mic-off glyph and the sub-line "Your mic is muted until they stop", and a neutral grey "Channel clear" — design the dial so all three read instantly.)
2. Below it a very large push-to-talk slab reading "HOLD TO TALK", spanning nearly the full width, unmistakably the biggest tappable thing on screen. (In its held state it turns solid green and reads "TALKING".)
3. A horizontal microphone level meter.
4. A link-statistics card of small label/value rows: state, round trip in ms, frame loss %, jitter buffer depth, role, music ducking, call time.
5. A red "End call" button at the bottom.

Direction for this variant: lean into the gauge/instrument-cluster idea. Let the circular indicator behave like a dial or readout you'd find in a cockpit — arcs, rings, a sweep, tick marks, whatever you choose — and let the statistics card read like telemetry. Precision instrument, not decoration.

Strict colour language — dark theme only, never shown in light mode:
background #101418, card surfaces #1E252C, accent #4FC3F7, talking green #66BB6A, buddy amber #FFB74D, end-call red #EF5350, primary text #E6EDF3, dimmed text #8A99A6, rounded cards roughly 16dp.

Format: one Android phone screen, portrait, 9:19.5, edge to edge, all text legible and plausible as real interface copy.

Rendering style is your call — flat, dimensional, textured, glass, engraved, anything — as long as it is coherent, high contrast and survives being glanced at in sunlight. Mood/lighting: in motion, alert, cold clear daylight on a dark panel.
```

## 2. Call screen — Variant B (type is the interface)

```
UI design concept: the main in-ride screen of an Android app called "Rider Intercom".

What the app is: a direct rider-to-rider intercom for motorcyclists. Two phones link over Bluetooth — no internet, no phone call — and carry voice while each rider's own music keeps playing. Voice-activated: whoever speaks first takes the channel, the other's mic auto-mutes.

Who looks at this screen: a rider at speed, glancing for a fraction of a second at a bar-mounted phone, in daylight, through a visor, in gloves. State recognition beats beauty every time.

Content and hierarchy, top to bottom:
1. A large circular status indicator with a microphone glyph and a state label, shown here in the green #66BB6A "You are talking" state. (It must also work as amber #FFB74D "<Buddy> is talking" with a mic-off glyph and the sub-line "Your mic is muted until they stop", and as a neutral grey "Channel clear".)
2. A very large "HOLD TO TALK" push-to-talk slab, nearly full width, the dominant control on the screen. (Held state: solid green, reads "TALKING".)
3. A horizontal microphone level meter.
4. A link-statistics card as compact label/value rows: state, round trip in ms, frame loss %, jitter buffer depth, role, music ducking, call time.
5. A red "End call" button at the bottom.

Direction for this variant: push an extreme, almost brutal typographic approach. Let oversized words carry the state rather than ornament — the status text enormous, the controls plain and declarative, generous empty space, almost no decoration, ruthless hierarchy. Fewer elements, bigger. Something a rider could read at 100 km/h with their peripheral vision.

Strict colour language — dark theme only, never light mode:
background #101418, cards #1E252C, accent #4FC3F7, talking green #66BB6A, buddy amber #FFB74D, end-call red #EF5350, primary text #E6EDF3, dimmed text #8A99A6, rounded cards roughly 16dp.

Format: one Android phone screen, portrait, 9:19.5, edge to edge, realistic interface copy.

Choose the typeface character, weights, grid and any surface treatment yourself; just stay inside the palette and keep it savagely legible. Mood/lighting: stark, functional, high-noon clarity.
```

## 2. Call screen — Variant C (gloved, tactile, physical)

```
UI design concept: the main in-ride screen of an Android app called "Rider Intercom".

What the app is: a direct rider-to-rider intercom for motorcyclists. Two phones link over Bluetooth — no internet, no cellular call — and carry voice while each rider's own music keeps playing. Voice-activated: first speaker takes the channel, the other's mic auto-mutes.

Who looks at this screen: a rider in motion, glancing at a bar-mounted phone in daylight through a helmet visor, pressing with a thick glove. Targets must look like they can be hit blind, by feel, with a clumsy thumb.

Content and hierarchy, top to bottom:
1. A large circular status indicator with a microphone glyph and a state label — shown here green #66BB6A, "You are talking". (It also has to read as amber #FFB74D "<Buddy> is talking" with a mic-off glyph plus "Your mic is muted until they stop", and a neutral grey "Channel clear".)
2. A very large "HOLD TO TALK" slab, nearly the full width and tall enough to hit without looking. (Held: solid green, reads "TALKING".)
3. A horizontal microphone level meter.
4. A link-statistics card of label/value rows: state, round trip in ms, frame loss %, jitter buffer depth, role, music ducking, call time.
5. A red "End call" button at the bottom.

Direction for this variant: make the controls feel physically operable — chunky, tactile, like moulded switchgear or a rubberised handlebar control pod rendered in software. Depth, edges, a sense of press and travel; controls that look pressable rather than drawn. Information rows stay quiet and flat so the controls dominate.

Strict colour language — dark theme only, never light mode:
background #101418, cards #1E252C, accent #4FC3F7, talking green #66BB6A, buddy amber #FFB74D, end-call red #EF5350, primary text #E6EDF3, dimmed text #8A99A6, rounded cards roughly 16dp.

Format: one Android phone screen, portrait, 9:19.5, edge to edge, plausible real interface copy.

The exact material language, shading, iconography and type are yours to choose — go all in on one idea. Mood/lighting: rugged, weatherproof, daylight falling across a dark control surface.
```

---

## 3. Settings screen

```
UI design concept: the settings screen of an Android app called "Rider Intercom".

What the app is: a direct rider-to-rider intercom for motorcyclists. Two phones link over Bluetooth — no internet, no phone call — and carry voice while each rider's own music keeps playing. Voice-activated: whoever speaks first takes the channel.

Who uses this screen: a motorcyclist, sometimes setting up before a ride, sometimes adjusting at a stop with gloves on, in daylight. It is a utility instrument, not a consumer social app: high contrast, large type, few colours, big touch targets, explanations short and plain. Settings here are about audio trade-offs, so every option needs a one-line reason, not just a name.

Content: a vertically scrolling list of grouped cards, each card a titled section:
1. "Microphone source" — four radio options, each with a one-line trade-off explanation underneath, one selected.
2. "Voice detection sensitivity" — three radio options with short explanations.
3. "Transport" — three radio options, one of them visibly disabled/greyed out with a short reason.
4. A voice-level slider with a numeric dB value displayed.
5. Several toggle rows, each with a title and an explanatory subtitle: duck music while talking, play a beep when muted, use the headset button.
Show the screen mid-scroll so it clearly reads as a long list, with a card partially cut off by the bottom edge.

Strict colour language — dark theme only, this app is never shown in light mode:
background #101418, card surfaces #1E252C, accent / selected state #4FC3F7, primary text #E6EDF3, dimmed secondary text #8A99A6, danger red #EF5350 if needed, rounded cards roughly 16dp corner radius. Disabled items should be clearly, legibly inactive without becoming invisible.

Format: one Android phone screen, portrait, 9:19.5, edge to edge. Radio buttons, toggles and the slider must be unmistakably those controls at a glance, and the text must read as real settings copy.

Rendering style, type treatment, spacing rhythm, control shapes and section headers are yours to decide — pick a coherent direction and hold it across every card. Mood/lighting: calm, technical, workshop-before-the-ride.
```

---

## 4. Noise suppression settings card

```
UI design concept: a single focused settings card from an Android app called "Rider Intercom".

What the app is: a direct rider-to-rider intercom for motorcyclists. Two phones link over Bluetooth — no internet, no phone call — and carry voice while each rider's own music keeps playing. Wind and engine noise are the enemy, so this control matters.

Who reads it: a motorcyclist, in daylight, possibly through a visor, possibly in gloves. High contrast, large type, few colours, short plain explanations, no clutter.

Content of the card:
- A card title about noise suppression, with a short line of supporting text.
- Three radio options stacked: "Off", "Light", "Strong" — each with a one-line explanation of what it does and what it costs.
- A closing note, visually quieter than the options, making clear that this cleans up the voice being transmitted to the other rider rather than cancelling noise at your own ear.

Composition: show the card as the hero of the image — a single rounded card roughly 16dp corner radius, floating on the app's dark background, with a little of the surrounding screen implied above and below so it still reads as part of a real phone UI rather than a loose graphic.

Strict colour language — dark theme only, never shown in light mode:
background #101418, card surface #1E252C, accent / selected radio #4FC3F7, primary text #E6EDF3, dimmed secondary text #8A99A6.

Format: Android phone screen, portrait, 9:19.5. Text must read as genuine, concise interface copy.

The visual direction is entirely yours — how the radio options are expressed, how the explanations sit, whether the three levels get any visual indication of intensity, type and surface treatment. Choose one idea and execute it fully. Mood/lighting: quiet, precise, considered.
```

---

## 5. Background / battery guidance screen

```
UI design concept: an instructional screen inside an Android app called "Rider Intercom".

What the app is: a direct rider-to-rider intercom for motorcyclists. Two phones link over Bluetooth — no internet, no phone call — and carry voice while each rider's own music keeps playing. The problem this screen solves: Android phone manufacturers ship aggressive battery managers that kill background apps, which silently drops the intercom mid-ride.

Who reads it: a motorcyclist setting up before a ride, in daylight, possibly in gloves, who wants to be done in thirty seconds. So: high contrast, large type, few colours, big touch targets, short numbered instructions in plain language, no walls of text, no clutter. It is a utility instrument, not a consumer social app.

Content and hierarchy, top to bottom:
1. A clear headline and one-line explanation of why this matters — the phone's battery manager can end the call mid-ride.
2. A numbered sequence of step cards, each card carrying a visible step number, a short instruction, and at least one with a button that opens the relevant system settings screen.
3. Some closing reassurance or a note that the exact menu names vary by phone brand.
Show it as a scrolling screen with the last step card partially cut off at the bottom edge.

Strict colour language — dark theme only, never shown in light mode:
background #101418, card surfaces #1E252C, accent / primary buttons #4FC3F7, primary text #E6EDF3, dimmed secondary text #8A99A6, danger red #EF5350 for the warning emphasis if you want it, rounded cards roughly 16dp corner radius.

Format: one Android phone screen, portrait, 9:19.5, edge to edge, all copy plausible and readable.

How you express the step numbering, the progression between steps, the button styling and any supporting iconography is up to you — pick a direction and apply it consistently. Mood/lighting: helpful, matter-of-fact, mildly urgent without being alarming.
```

---

## 6. App logo / launcher icon — Variant A (two riders, negative space)

```
Logo / Android launcher icon design for an app called "Rider Intercom".

What the app is: a direct rider-to-rider intercom for motorcyclists. Two riders' phones link to each other over Bluetooth — no internet, no phone call — and carry voice between them while each rider's own music keeps playing. The idea the mark must carry is "two riders talking" / "a live voice link between two people".

Direction for this variant: build the mark from two riders. Think helmets, visors, two forms in formation, one behind the other — abstracted hard into geometry, with meaning carried by the negative space between the two forms. Reductive and symbolic, not illustrative. The viewer should get it in under a second without being shown a literal picture of a motorcycle.

Hard requirements:
- Must survive as a small Android adaptive icon: readable at 48px, one dominant idea, no fine lines, no small text, no detail that collapses when shrunk.
- Must also scale up cleanly to a large presentation mark.
- Must sit comfortably inside a circle AND a rounded square mask — keep the mark centred with safe margins, nothing important near the edges.
- No lettering, no wordmark, no slogans.

Palette — dark theme only, this brand is never shown on white:
background #101418, accent / primary #4FC3F7 (light blue), optional supporting green #66BB6A and amber #FFB74D if the idea needs a second and third tone, light text colour #E6EDF3 for high-contrast shapes. Keep the colour count low and the contrast against the dark background strong.

Format: square, 1:1, mark centred on the dark background. Also acceptable to show the mark alone, crisp, no mockup clutter.

Rendering style is yours — flat vector, dimensional, cut-paper, engraved badge, whatever you think gives the strongest instant read. Commit fully. Mood: equipment-grade, confident, built for the road.

Avoid: generic headset icons, generic rounded speech bubbles, clip-art motorcycles, stock Bluetooth glyphs, walkie-talkies, and cliché "signal arc" radiating lines used on their own.
```

## 6. App logo / launcher icon — Variant B (the voice link itself)

```
Logo / Android launcher icon design for an app called "Rider Intercom".

What the app is: a direct rider-to-rider intercom for motorcyclists. Two riders' phones link directly over Bluetooth — no internet, no phone call — and carry voice while each rider's own music keeps playing. Only one rider holds the channel at a time: whoever speaks first takes it, the other listens.

Direction for this variant: make the link the hero rather than the riders. A mark built from the connection between two points — two anchor forms with something living travelling between them: a voice waveform, a channel, a taut line, a bridge. The geometry should imply direction and a point-to-point private connection rather than a broadcast to everyone. Abstract, structural, almost like a technical symbol or a mark you'd find on instrument panels.

Hard requirements:
- Must work as a small Android adaptive icon: readable at 48px, a single strong silhouette, thick confident forms, no hairlines, no text.
- Must also scale up cleanly as a large brand mark.
- Must sit inside both a circle and a rounded-square mask — centred, with safe margins on all sides.
- No lettering or wordmark.

Palette — dark theme only, the brand is never shown on white:
background #101418, accent / primary #4FC3F7 (light blue), optional green #66BB6A and amber #FFB74D as a second and third tone (they mean "you are talking" and "your buddy is talking", so a two-tone split can carry real meaning), light shape colour #E6EDF3. Keep the palette tight and the contrast high.

Format: square, 1:1, mark centred on the dark background, no mockup clutter.

Choose the rendering style yourself — flat geometric, dimensional, monoline, cut-out, whatever makes the idea land hardest — and execute it with full conviction. Mood: precise, technical, trustworthy.

Avoid: generic headsets, generic speech bubbles, clip-art motorcycles, the stock Bluetooth glyph, and plain concentric "wifi" arcs used on their own.
```

## 6. App logo / launcher icon — Variant C (emblem / badge)

```
Logo / Android launcher icon design for an app called "Rider Intercom".

What the app is: a direct rider-to-rider intercom for motorcyclists. Two riders' phones link directly over Bluetooth — no internet, no phone call — and carry voice between them while each rider's own music keeps playing. The mark has to say "two riders talking" or "voice link" at a glance.

Direction for this variant: go emblem. A single compact badge-like mark with the density and authority of motorcycle-club and motorsport insignia, or of a stamped equipment mark on a piece of gear — but modernised and stripped to geometry, so it still survives being shrunk to a launcher icon. Enclosed form, strong symmetry or deliberate asymmetry, one memorable silhouette. The meaning of two riders and a live voice channel should be built into the emblem's structure rather than bolted on.

Hard requirements:
- Must read as a small Android adaptive icon at 48px: one dominant shape, heavy enough strokes, zero fine detail, no text inside the mark.
- Must also scale up to a large mark with presence.
- Must fit both a circle and a rounded-square mask, centred with safe margins.
- No lettering, no wordmark, no banner ribbons with text.

Palette — dark theme only, never shown on white:
background #101418, accent / primary #4FC3F7 (light blue), optional supporting #66BB6A green and #FFB74D amber, light shape colour #E6EDF3. Few colours, hard contrast against the dark.

Format: square, 1:1, centred mark on dark, clean, no mockup props.

Pick the rendering approach yourself — flat, engraved, embossed metal, screen-printed, anything — and commit to it fully and consistently. Mood: earned, durable, a mark that belongs on riding equipment.

Avoid: generic headset icons, generic speech bubbles, clip-art motorcycles, the stock Bluetooth symbol, skulls, wings, flames, and vintage-biker pastiche.
```

---

## 7. Play Store feature graphic

```
Google Play Store feature graphic for an Android app called "Rider Intercom".

What the app is: a direct rider-to-rider intercom for motorcyclists. Two riders' phones link to each other over Bluetooth — no internet, no cellular call — and carry voice between them while each rider's own music keeps playing. It is voice-activated: whoever speaks first takes the channel and the other's mic automatically mutes.

Job of this banner: one glance, one idea — two riders, talking, no network needed. A store browser should understand the product before reading a single word.

Content:
- The app name "Rider Intercom" and a very short tagline (a handful of words, something along the lines of talking rider to rider with no network). Keep text minimal and large.
- A visual that carries the two-riders-connected idea. You may use abstract forms, the app's own dark interface as an object in the composition, or a stylised depiction of two riders — your choice, but it must not be a cluttered collage of screenshots.
- Leave the composition safe for cropping: Play shows this banner at different widths and may overlay elements, so keep the title and any critical shape well inside the centre with clear margins, and nothing important in the far corners.

Strict colour language — dark theme only, this brand is never shown light:
background #101418, card surface #1E252C, accent / primary #4FC3F7 (light blue), "you are talking" green #66BB6A, "buddy is talking" amber #FFB74D, red #EF5350 used sparingly if at all, primary text #E6EDF3, dimmed text #8A99A6.

Format: wide banner, roughly 16:9, designed to still work when cropped toward the 1024x500 Play Store proportion. High contrast, readable as a thumbnail.

Art direction is open — photographic, illustrative, graphic, abstract, typographic — pick one strong direction and execute it completely rather than hedging. Mood/lighting: open road, daylight, motion, equipment you rely on.
```

---

## Negative prompts — applies to every image above

Append any of these that your tool supports, or state them as "avoid" lines:

```
Avoid: light mode, white or pale backgrounds, colours outside the stated palette, purple/pink/teal gradients, rainbow accents, neon cyberpunk glow, heavy glassmorphism blur that hurts legibility, thin or low-contrast grey-on-grey text, tiny type, cramped touch targets, dense multi-column layouts, decorative illustration that competes with the controls, stock-photo smiling people, lens flares, confetti, emoji, drop-shadow pileups, skeuomorphic leather or carbon-fibre texture cliché, fake chrome, clip-art motorcycles, generic headset icons, generic speech bubbles, the stock Bluetooth glyph, walkie-talkies, vintage-biker skulls/wings/flames, lorem ipsum, garbled or misspelled text, watermark, signature, UI chrome from iOS, mismatched icon sets, more than one design language in a single image.
```

### Notes for whoever runs these

- Phone-screen prompts are written for a clean screen render, not a mockup in a hand. If
  you want a device mockup, add one line: "show it inside a plain dark phone frame, no
  hands, no background scene."
- The Call screen prompts render the green "You are talking" state. To get the other two
  states, swap that one sentence to amber "<Buddy> is talking" with the mic-off glyph and
  the "Your mic is muted until they stop" sub-line, or to the neutral grey "Channel
  clear" — everything else in the prompt stays identical, which keeps the three renders
  comparable.
- Text accuracy is the weak point of every image generator. Expect to regenerate for
  legible labels, and treat the output as design direction rather than as a spec.
- The palette in these prompts is the one actually compiled into the app
  (`ui/Theme.kt`). If you change the app's colours, change these prompts too.
