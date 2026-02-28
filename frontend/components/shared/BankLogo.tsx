import Image from 'next/image'

// Map institution identifier → { logo file, brand color }
const BANK_MAP: Record<string, { file: string; color: string; bgColor: string }> = {
  CHASE:           { file: 'chase.svg',           color: '#117ACA', bgColor: '#0A4F8A' },
  AMEX:            { file: 'amex.svg',             color: '#016FD0', bgColor: '#013B6E' },
  AMERICAN_EXPRESS:{ file: 'amex.svg',             color: '#016FD0', bgColor: '#013B6E' },
  BANK_OF_AMERICA: { file: 'bank-of-america.svg',  color: '#E31837', bgColor: '#8B0F1F' },
  CITI:            { file: 'citi.svg',             color: '#003B70', bgColor: '#002244' },
  CITIBANK:        { file: 'citi.svg',             color: '#003B70', bgColor: '#002244' },
  WELLS_FARGO:     { file: 'wells-fargo.svg',      color: '#D71E2B', bgColor: '#8B1218' },
  CAPITAL_ONE:     { file: 'capital-one.svg',      color: '#004977', bgColor: '#002D4A' },
  DISCOVER:        { file: 'discover.svg',         color: '#F76000', bgColor: '#A84200' },
  GOLDMAN_SACHS:   { file: 'goldman-sachs.svg',    color: '#6C8EBF', bgColor: '#2B4A70' },
  APPLE:           { file: 'apple.svg',            color: '#555555', bgColor: '#222222' },
}

// Detect card network from card name or institution
function getNetworkLogo(cardName: string, institution: string | null): string | null {
  const name = (cardName + ' ' + (institution || '')).toUpperCase()
  if (institution === 'AMEX' || institution === 'AMERICAN_EXPRESS') return 'amex.svg'
  if (name.includes('VISA'))       return 'visa.svg'
  if (name.includes('MASTERCARD')) return 'mastercard.svg'
  if (name.includes('DISCOVER'))   return 'discover.svg'
  if (name.includes('AMEX') || name.includes('AMERICAN EXPRESS')) return 'amex.svg'
  return null
}

interface BankLogoProps {
  institution: string | null
  size?: number
  white?: boolean   // true = render white (for dark card headers)
  className?: string
}

export function BankLogo({ institution, size = 28, white = false, className = '' }: BankLogoProps) {
  const key = (institution || '').toUpperCase().replace(/[^A-Z_]/g, '_')
  const bank = BANK_MAP[key]
  if (!bank) return null

  return (
    <Image
      src={`/card-logos/${bank.file}`}
      alt={institution || 'Bank'}
      width={size}
      height={size}
      className={className}
      style={white ? { filter: 'brightness(0) invert(1)' } : undefined}
      unoptimized
    />
  )
}

interface CardNetworkLogoProps {
  cardName: string
  institution: string | null
  size?: number
  white?: boolean
  className?: string
}

export function CardNetworkLogo({ cardName, institution, size = 32, white = false, className = '' }: CardNetworkLogoProps) {
  const file = getNetworkLogo(cardName, institution)
  if (!file) return null

  return (
    <Image
      src={`/card-logos/${file}`}
      alt="Card network"
      width={size}
      height={size}
      className={className}
      style={white ? { filter: 'brightness(0) invert(1)' } : undefined}
      unoptimized
    />
  )
}

export function getBankColor(institution: string | null): string {
  const key = (institution || '').toUpperCase().replace(/[^A-Z_]/g, '_')
  return BANK_MAP[key]?.color || '#1E1E2E'
}

export function getBankBgColor(institution: string | null): string {
  const key = (institution || '').toUpperCase().replace(/[^A-Z_]/g, '_')
  return BANK_MAP[key]?.bgColor || '#16161F'
}
