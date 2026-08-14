export interface SkillIconSource {
  id?: string
  skillId?: string
  passiveId?: string
  skillType?: string
  effectType?: string
  damageType?: string
}

const skillTypeRules: Array<[RegExp, string]> = [
  [/^(PASSIVE_)/, 'passive_badge'],
  [/(LEAVE_AT_ONE_HP|LEAVE_ALIVE)/, 'capture_assist'],
  [/(LIFE_DRAIN|DRAIN)/, 'drain'],
  [/(POISON|TOXIC)/, 'poison'],
  [/(PURIFY|ELIMINATE|DISPEL|SHIELD_BREAK)/, 'dispel'],
  [/(COUNTER)/, 'counter'],
  [/(TAUNT)/, 'taunt'],
  [/(ENTANGLE|GUST_TRAP)/, 'bind'],
  [/(SILENT_FOG|NIGHT_SHROUD|SHADOW_CLONE)/, 'mist'],
  [/(CURSE|ANTI_HEAL|THUNDER_WAVE|MARK_TARGET)/, 'debuff_down'],
  [/(FIRE_CLAW|METAL_CLAW|VINE_WHIP|THORN_BIND|WATER_WHIP|BRANCH_STRIKE)/, 'claw_slash'],
  [/(FLAME_BURST|LIGHT_BURST|ROCK_BLAST|INFERNO|THUNDER_STORM|CHAIN_LIGHTNING)/, 'explosion'],
  [/(METEOR_FALL|ROCK_THROW)/, 'falling'],
  [/(WATER_PULSE|TIDAL_WAVE|TORRENT)/, 'wave'],
  [/(BOOK_)/, 'book_common'],
]

export function elementIconUrl(element: string): string {
  return `/assets/ui/elements/icon_element_${element.toLowerCase()}.png`
}

export function statusIconUrl(statusId: string): string {
  return `/assets/ui/statuses/icon_status_${statusId.toLowerCase()}.png`
}

export function itemIconUrl(itemId: string): string {
  return `/assets/items/item_${itemId}.png`
}

export function skillTypeIconUrl(skill: SkillIconSource): string {
  const id = (skill.id ?? skill.skillId ?? skill.passiveId ?? '').toUpperCase()
  let type = skill.skillType === 'PASSIVE' || skill.passiveId ? 'passive_badge' : ''
  if (!type) type = skillTypeRules.find(([pattern]) => pattern.test(id))?.[1] ?? ''
  if (!type && skill.effectType === 'HEAL') type = 'heal'
  if (!type && skill.effectType === 'SHIELD') type = 'shield'
  if (!type && skill.damageType === 'PHYSICAL') type = 'physical_strike'
  if (!type && skill.damageType === 'MAGICAL') type = 'projectile'
  return `/assets/ui/skill-types/icon_skilltype_${type || 'buff_up'}.png`
}
