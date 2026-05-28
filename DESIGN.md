# DESIGN.md

## Purpose

This file captures the visual style extracted from the provided B-side dashboard reference image. Use it as the design direction when migrating the style into the AI Interview System.

The target feel is a calm, premium, minimal B-end product interface: grayscale spatial background, soft glass panels, compact dark navigation, crisp typography, and a small warm coral accent for action and status.

## Design Keywords

- Minimal B-end dashboard
- Soft glassmorphism
- Light neumorphic surfaces
- Grayscale spatial depth
- Compact enterprise navigation
- Coral-orange action accent
- Data-first but breathable
- Premium, quiet, focused

## Overall Impression

The UI should feel like a polished operating console rather than a marketing page. It uses large rounded surfaces, semi-transparent white panels, soft shadows, and a restrained monochrome palette. Interaction areas are clear but not loud. The warm accent color is used sparingly to guide attention.

For this project, apply the style especially to teacher/admin dashboards, assessment management, student records, evaluation reports, and interview summary pages. Do not turn operational screens into decorative landing pages. The design should remain efficient for repeated use.

## Visual System

### Color Palette

Use grayscale as the foundation. Use coral-orange only for primary actions, active indicators, progress highlights, alerts that require attention, and important chart accents.

```css
:root {
  /* Page and surfaces */
  --color-page-bg: #d8d8d6;
  --color-page-bg-deep: #747573;
  --color-surface: rgba(255, 255, 255, 0.82);
  --color-surface-solid: #f7f7f5;
  --color-surface-soft: rgba(255, 255, 255, 0.58);
  --color-surface-raised: #ffffff;

  /* Text */
  --color-text-primary: #17181a;
  --color-text-secondary: #5d6063;
  --color-text-muted: #8a8d90;
  --color-text-inverse: #f8f8f6;

  /* Borders and dividers */
  --color-border-light: rgba(255, 255, 255, 0.72);
  --color-border-subtle: rgba(24, 25, 27, 0.08);
  --color-border-strong: rgba(24, 25, 27, 0.16);

  /* Navigation */
  --color-sidebar-bg: #303130;
  --color-sidebar-bg-soft: #3b3c3b;
  --color-sidebar-muted: rgba(255, 255, 255, 0.58);

  /* Accent */
  --color-accent: #e75f49;
  --color-accent-hover: #f07157;
  --color-accent-soft: rgba(231, 95, 73, 0.14);
  --color-accent-glow: rgba(231, 95, 73, 0.32);

  /* Status */
  --color-success: #2f8f69;
  --color-warning: #d89a35;
  --color-danger: #d84a3a;
  --color-info: #4f6f8f;
}
```

### Color Usage Rules

- Backgrounds should be neutral gray or off-white, never pure saturated color.
- Main content panels should be translucent white or soft off-white.
- Dark areas are reserved for the sidebar, floating CTA panels, and selected metric cards.
- Coral-orange should cover less than 8% of any screen.
- Avoid blue-heavy enterprise defaults unless required for semantic status.
- Avoid strong gradients, colorful illustrations, and large decorative blobs.

## Typography

Use a modern sans-serif stack that works for Chinese and English.

```css
font-family:
  Inter,
  "SF Pro Display",
  "PingFang SC",
  "Microsoft YaHei",
  Arial,
  sans-serif;
```

### Type Scale

- Page title: 36-48px, 600-700 weight, line-height 1.05-1.12
- Section title: 18-22px, 600 weight
- Card title: 14-16px, 600 weight
- Body text: 13-14px, 400-500 weight
- Metadata: 11-12px, 400 weight
- Numeric metrics: 22-32px, 600-700 weight

### Typography Rules

- Use strong black text for primary meaning.
- Keep secondary text quiet and gray.
- Avoid oversized typography inside compact dashboards.
- Use numbers as visual anchors in metric cards.
- Chinese labels should be concise. Prefer 2-6 characters for navigation and tabs.

## Layout

### Page Structure

Desktop dashboard pages should use a staged layout:

- A full-viewport gray background.
- A compact dark sidebar on the left.
- A large rounded main workspace panel.
- Inner cards arranged in a clean dashboard grid.

Recommended shell:

```css
.app-shell {
  min-height: 100vh;
  padding: 32px;
  background:
    linear-gradient(180deg, rgba(255,255,255,0.32), rgba(0,0,0,0.18)),
    var(--color-page-bg);
}

.workspace {
  display: grid;
  grid-template-columns: 168px minmax(0, 1fr);
  gap: 16px;
  max-width: 1440px;
  margin: 0 auto;
}
```

### Main Panel

The main panel is the central visual object. It should feel like a frosted white slab.

```css
.main-panel {
  border-radius: 28px;
  background: var(--color-surface);
  border: 1px solid var(--color-border-light);
  box-shadow:
    0 28px 80px rgba(20, 21, 22, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(22px);
  overflow: hidden;
}
```

### Spacing

- Page padding: 24-40px desktop, 16px mobile
- Main panel padding: 28-36px desktop, 18-20px mobile
- Card padding: 16-24px
- Grid gap: 14-20px
- Dense table cell padding: 12-16px horizontal

Use breathing room around important summaries, but keep management tables compact.

## Surfaces and Depth

This style depends on layered soft depth. Use subtle shadows and inner highlights instead of heavy borders.

### Surface Levels

- Level 0: gray page background
- Level 1: dark sidebar and main white workspace
- Level 2: translucent cards inside the workspace
- Level 3: floating pills, active badges, buttons, chart overlays

```css
.soft-card {
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.68);
  box-shadow:
    0 12px 28px rgba(28, 29, 30, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.dark-card {
  border-radius: 22px;
  background: linear-gradient(145deg, #444544, #2f302f);
  color: var(--color-text-inverse);
  box-shadow:
    0 18px 44px rgba(20, 21, 22, 0.22),
    inset 0 1px 0 rgba(255, 255, 255, 0.08);
}
```

### Radius

- Main panel: 28px
- Sidebar: 24px
- Dashboard cards: 18-22px
- Buttons and inputs: 999px for pills, 12-14px for compact rectangular actions
- Icons: circular containers, 32-44px

## Navigation

### Sidebar

The sidebar is narrow, dark, and calm. It should not compete with the main content.

- Width: 150-180px desktop
- Background: dark charcoal
- Radius: 24px
- Items: icon circle plus short label
- Active item: bright icon circle or soft white highlight
- Labels: small, muted, high legibility
- Bottom area: compact CTA/help card

```css
.sidebar {
  border-radius: 24px;
  background: linear-gradient(180deg, #3d3e3d, #2d2e2d);
  color: var(--color-text-inverse);
  box-shadow: 0 24px 70px rgba(18, 19, 20, 0.22);
}

.nav-item {
  height: 44px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  color: var(--color-sidebar-muted);
  font-size: 12px;
}

.nav-item.active {
  color: #ffffff;
}

.nav-icon {
  width: 30px;
  height: 30px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.08);
}

.nav-item.active .nav-icon {
  background: #ffffff;
  color: var(--color-accent);
}
```

## Components

### Buttons

Primary buttons should be compact, pill-like, and warm. They should look tactile but not glossy.

```css
.btn-primary {
  height: 36px;
  padding: 0 18px;
  border-radius: 999px;
  border: 0;
  background: var(--color-accent);
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 10px 24px var(--color-accent-glow);
}

.btn-secondary {
  height: 36px;
  padding: 0 18px;
  border-radius: 999px;
  border: 1px solid var(--color-border-subtle);
  background: rgba(255, 255, 255, 0.72);
  color: var(--color-text-primary);
}
```

Use icon-only buttons for frequent tools such as refresh, export, filter, edit, delete, play, pause, upload, and settings. Add tooltips for icon-only actions.

### Inputs and Search

Inputs are soft pills or rounded rectangles with low contrast.

```css
.input-soft {
  height: 42px;
  border-radius: 999px;
  border: 1px solid rgba(24, 25, 27, 0.08);
  background: rgba(255, 255, 255, 0.78);
  box-shadow: inset 0 1px 4px rgba(20, 21, 22, 0.05);
}
```

For B-end forms, use rounded rectangles instead of pills when fields are stacked or long.

### Cards

Use cards for dashboards, summaries, charts, and repeated items. Avoid nesting cards inside cards.

Card types:

- Summary card: big number, short label, small trend or badge
- Progress card: compact title, progress bar, target value
- Chart card: minimal axes, few labels, large whitespace
- Dark highlight card: one important KPI or recommendation
- Chat/AI card: message bubbles, soft input pill, small avatar/badge

### Tables

Tables should inherit the calm visual system but remain dense and readable.

- Header background: transparent or very light gray
- Row height: 48-56px
- Borders: subtle horizontal dividers
- Hover: soft white overlay
- Actions: icon buttons or compact text buttons
- Status: small rounded badges

Avoid heavy table borders and saturated row backgrounds.

### Tabs and Segmented Controls

Use small pill segmented controls for filters such as daily, weekly, monthly, role, status, and assessment state.

```css
.segmented {
  display: inline-flex;
  padding: 3px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.42);
}

.segmented-item {
  min-width: 68px;
  height: 30px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  color: var(--color-text-secondary);
}

.segmented-item.active {
  background: #ffffff;
  color: var(--color-text-primary);
  box-shadow: 0 8px 18px rgba(24, 25, 27, 0.08);
}
```

### Badges

Badges should be small and quiet.

- Active/success: muted green
- Pending/warning: muted amber
- Failed/danger: coral-red
- AI/generation: coral-orange pill with subtle glow

## Charts and Data Visualization

Charts should be minimal, tactile, and readable.

- Use black or charcoal for primary bars/lines.
- Use coral-orange for the selected or most important data point.
- Use pale gray for inactive series.
- Hide unnecessary grid lines.
- Keep labels short.
- Prefer rounded bar caps and thick progress arcs.

For ECharts:

```js
const chartStyle = {
  color: ["#17181a", "#e75f49", "#d9dcde"],
  textStyle: {
    color: "#5d6063",
    fontFamily: "Inter, PingFang SC, Microsoft YaHei, Arial, sans-serif"
  },
  grid: {
    left: 12,
    right: 12,
    top: 20,
    bottom: 12,
    containLabel: true
  }
};
```

## Iconography

Use simple filled or rounded-line icons. In this project, prefer Ant Design Vue icons because the dependency already exists.

Icon rules:

- Icons sit inside circular soft containers.
- Use charcoal icons on light surfaces.
- Use white icons on dark surfaces.
- Use coral only for active, alert, or primary-action icons.
- Keep icon sizes between 14px and 18px inside nav/card controls.

## Motion

Motion should be subtle and functional.

- Hover lift: translateY(-1px) or translateY(-2px)
- Transition duration: 160-220ms
- Easing: cubic-bezier(0.2, 0.8, 0.2, 1)
- Avoid bouncy, playful, or large page animations.
- Use soft fade/slide for modal and drawer entrances.

```css
.interactive {
  transition:
    transform 180ms cubic-bezier(0.2, 0.8, 0.2, 1),
    box-shadow 180ms cubic-bezier(0.2, 0.8, 0.2, 1),
    background-color 180ms ease;
}

.interactive:hover {
  transform: translateY(-1px);
}
```

## Responsive Behavior

### Desktop

- Keep sidebar visible.
- Use 2-4 column dashboard grids.
- Keep the main panel centered with max width.
- Avoid full-bleed tables without visual containment.

### Tablet

- Collapse sidebar into a narrower icon rail.
- Convert dense grids to 2 columns.
- Keep cards at stable heights where possible.

### Mobile

- Use a single-column layout.
- Replace sidebar with top navigation or bottom tab navigation.
- Reduce main panel radius to 20px.
- Avoid tiny chart labels. Prefer summaries and expandable details.

## Application Mapping

### Admin Dashboard

Use this style strongly:

- Dark sidebar
- Frosted main panel
- KPI cards
- Soft table rows
- Small segmented filters
- Coral active states

### Teacher Dashboard

Use this style for:

- Assessment configuration cards
- Student progress summaries
- Appointment status dashboards
- Record review pages
- Score update modals

### Student Dashboard

Use a slightly warmer, simpler version:

- Fewer dense widgets
- Larger primary actions
- More direct status cards
- Keep the same surface, radius, and accent language

### Interview Room

Use the style carefully:

- Keep controls highly legible and accessible.
- Use dark highlight surfaces for live voice/AI status.
- Use coral for recording, active question, and urgent state only.
- Avoid visual noise during live interaction.

### Result and Analysis Pages

This style fits very well:

- Use large score cards.
- Use soft chart cards.
- Use concise AI suggestion cards.
- Use dark cards for final evaluation highlights.

## Ant Design Vue Adaptation

When styling Ant Design Vue components, prefer token-level overrides and scoped wrappers.

Suggested direction:

- `borderRadius`: 14-20 for inputs, cards, modals
- `colorPrimary`: `#e75f49`
- `colorText`: `#17181a`
- `colorTextSecondary`: `#5d6063`
- `colorBorder`: `rgba(24,25,27,0.10)`
- `controlHeight`: 38-42
- `boxShadow`: soft neutral shadows only

Avoid the default bright blue Ant Design primary color unless it is required by an existing feature.

## Do

- Use grayscale depth, white translucent panels, and dark compact navigation.
- Use coral-orange as the only strong accent.
- Keep dashboard content clear, scannable, and operational.
- Use rounded cards, soft shadows, and thin borders.
- Make metrics and status obvious at a glance.
- Keep charts minimal and elegant.
- Use icon buttons with tooltips for frequent operations.

## Don't

- Do not use large marketing hero sections in the product app.
- Do not use saturated blue/purple gradients.
- Do not place cards inside cards.
- Do not overuse glass blur where text readability matters.
- Do not make tables too airy for management workflows.
- Do not use multiple competing accent colors on the same page.
- Do not copy any source brand logo or trademarked assets.

## Implementation Prompt For AI Agents

When implementing UI changes, follow this instruction:

> Read `DESIGN.md` first. Restyle the target page into a minimal B-end dashboard style with grayscale spatial background, dark compact sidebar, frosted white main panel, soft rounded cards, coral-orange accent states, and dense but readable operational content. Preserve existing data flow and user workflows. Do not add marketing sections. Do not copy brand logos or trademarked assets. Use existing Vue 3, Ant Design Vue, Tailwind CSS, and ECharts patterns where possible.

## Reference Summary

The reference image shows a minimal dashboard with:

- A dark charcoal vertical sidebar.
- A large frosted white rounded workspace.
- Small circular icon navigation.
- Soft cards and progress widgets.
- Coral-orange active badges and chart highlights.
- High-contrast black typography.
- Minimal charts with black, gray, and orange.
- Overall premium, quiet, grayscale B-end atmosphere.
