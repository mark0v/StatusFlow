# Design

## Product tone

StatusFlow should feel operational, clear, and trustworthy. This is not a playful consumer app. It should look like a compact workflow tool where state changes matter.

## Visual direction

- clean but not generic
- warm neutral base with one strong accent color
- dense enough for operators, readable enough for mobile users
- status colors must be meaningful and consistent across platforms

## Platform guidance

### Mobile

- simple task-focused layout
- status chips visible in lists and details
- strong empty, loading, and error states
- sync timestamp visible in detail and list screens

### Web

- table-first operator workflow
- filters for status and date
- clear action affordances for changing statuses
- comments and history visible without deep navigation

## Initial design tokens

- Primary accent: deep teal
- Success: muted green
- Warning: amber
- Error: brick red
- Surface: warm off-white
- Text: charcoal

## Design rules

- every status must have text + color, never color alone
- empty states should explain what to do next
- destructive actions must be clearly separated
- forms should expose validation clearly
- operator flows should optimize for speed and clarity

## Planned follow-up

Run `gstack` design consultation before implementation to lock:

- typography
- color palette hex values
- spacing scale
- mobile component style
- web admin visual language
