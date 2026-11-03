import type { ComponentType } from 'react';
import { ArrowClockwiseIcon as ArrowClockwise } from '@phosphor-icons/react/dist/csr/ArrowClockwise';
import { ArrowCounterClockwiseIcon as ArrowCounterClockwise } from '@phosphor-icons/react/dist/csr/ArrowCounterClockwise';
import { ArrowDownIcon as ArrowDown } from '@phosphor-icons/react/dist/csr/ArrowDown';
import { ArrowLeftIcon as ArrowLeft } from '@phosphor-icons/react/dist/csr/ArrowLeft';
import { ArrowRightIcon as ArrowRight } from '@phosphor-icons/react/dist/csr/ArrowRight';
import { ArrowSquareOutIcon as ArrowSquareOut } from '@phosphor-icons/react/dist/csr/ArrowSquareOut';
import { ArrowUpIcon as ArrowUp } from '@phosphor-icons/react/dist/csr/ArrowUp';
import { ArrowsLeftRightIcon as ArrowsLeftRight } from '@phosphor-icons/react/dist/csr/ArrowsLeftRight';
import { ArrowsOutSimpleIcon as ArrowsOutSimple } from '@phosphor-icons/react/dist/csr/ArrowsOutSimple';
import { BellIcon as Bell } from '@phosphor-icons/react/dist/csr/Bell';
import { CardsIcon as Cards } from '@phosphor-icons/react/dist/csr/Cards';
import { ChatCircleIcon as ChatCircle } from '@phosphor-icons/react/dist/csr/ChatCircle';
import { CheckIcon as Check } from '@phosphor-icons/react/dist/csr/Check';
import { CheckCircleIcon as CheckCircle } from '@phosphor-icons/react/dist/csr/CheckCircle';
import { CircleNotchIcon as CircleNotch } from '@phosphor-icons/react/dist/csr/CircleNotch';
import { ClipboardTextIcon as ClipboardText } from '@phosphor-icons/react/dist/csr/ClipboardText';
import { CodeIcon as Code } from '@phosphor-icons/react/dist/csr/Code';
import { CubeIcon as Cube } from '@phosphor-icons/react/dist/csr/Cube';
import { DotsSixVerticalIcon as DotsSixVertical } from '@phosphor-icons/react/dist/csr/DotsSixVertical';
import { DotsThreeIcon as DotsThree } from '@phosphor-icons/react/dist/csr/DotsThree';
import { FileTextIcon as FileText } from '@phosphor-icons/react/dist/csr/FileText';
import { FireIcon as Fire } from '@phosphor-icons/react/dist/csr/Fire';
import { FlaskIcon as Flask } from '@phosphor-icons/react/dist/csr/Flask';
import { FolderSimpleIcon as FolderSimple } from '@phosphor-icons/react/dist/csr/FolderSimple';
import { GearIcon as Gear } from '@phosphor-icons/react/dist/csr/Gear';
import { GithubLogoIcon as GithubLogo } from '@phosphor-icons/react/dist/csr/GithubLogo';
import { HandWavingIcon as HandWaving } from '@phosphor-icons/react/dist/csr/HandWaving';
import { HexagonIcon as Hexagon } from '@phosphor-icons/react/dist/csr/Hexagon';
import { HourglassIcon as Hourglass } from '@phosphor-icons/react/dist/csr/Hourglass';
import { HouseIcon as House } from '@phosphor-icons/react/dist/csr/House';
import { ListBulletsIcon as ListBullets } from '@phosphor-icons/react/dist/csr/ListBullets';
import { MagnifyingGlassIcon as MagnifyingGlass } from '@phosphor-icons/react/dist/csr/MagnifyingGlass';
import { PauseIcon as Pause } from '@phosphor-icons/react/dist/csr/Pause';
import { PlayIcon as Play } from '@phosphor-icons/react/dist/csr/Play';
import { PlusIcon as Plus } from '@phosphor-icons/react/dist/csr/Plus';
import { ProhibitIcon as Prohibit } from '@phosphor-icons/react/dist/csr/Prohibit';
import { PulseIcon as Pulse } from '@phosphor-icons/react/dist/csr/Pulse';
import { QuestionIcon as Question } from '@phosphor-icons/react/dist/csr/Question';
import { ShieldCheckIcon as ShieldCheck } from '@phosphor-icons/react/dist/csr/ShieldCheck';
import { SidebarSimpleIcon as SidebarSimple } from '@phosphor-icons/react/dist/csr/SidebarSimple';
import { SignOutIcon as SignOut } from '@phosphor-icons/react/dist/csr/SignOut';
import { SkipForwardIcon as SkipForward } from '@phosphor-icons/react/dist/csr/SkipForward';
import { SlidersHorizontalIcon as SlidersHorizontal } from '@phosphor-icons/react/dist/csr/SlidersHorizontal';
import { SquareIcon as Square } from '@phosphor-icons/react/dist/csr/Square';
import { SquaresFourIcon as SquaresFour } from '@phosphor-icons/react/dist/csr/SquaresFour';
import { TreeStructureIcon as TreeStructure } from '@phosphor-icons/react/dist/csr/TreeStructure';
import { UserCircleIcon as UserCircle } from '@phosphor-icons/react/dist/csr/UserCircle';
import { WarningIcon as Warning } from '@phosphor-icons/react/dist/csr/Warning';
import { XIcon as X } from '@phosphor-icons/react/dist/csr/X';
import type { IconProps as PhosphorIconProps } from '@phosphor-icons/react';
import { StopIcon as Stop } from '@phosphor-icons/react/dist/csr/Stop';
import { GitBranchIcon as GitBranch } from '@phosphor-icons/react/dist/csr/GitBranch';
import { SparkleIcon as Sparkle } from '@phosphor-icons/react/dist/csr/Sparkle';
import { LightningIcon as Lightning } from '@phosphor-icons/react/dist/csr/Lightning';
import { DownloadSimpleIcon as DownloadSimple } from '@phosphor-icons/react/dist/csr/DownloadSimple';
import { UploadSimpleIcon as UploadSimple } from '@phosphor-icons/react/dist/csr/UploadSimple';
import { CircleIcon as Circle } from '@phosphor-icons/react/dist/csr/Circle';
import { CloudIcon as Cloud } from '@phosphor-icons/react/dist/csr/Cloud';
import { cx } from '../cx';

import styles from './Icon.module.scss';

// Phosphor glyphs keyed by the app's own semantic names. Keeping the map here means call sites
// never import the icon set directly, so swapping or adding a glyph is a one-file change.
const ICONS = {
  check: Check,
  'check-circle': CheckCircle,
  close: X,
  refresh: ArrowClockwise,
  settings: Gear,
  search: MagnifyingGlass,
  plus: Plus,
  'arrow-left': ArrowLeft,
  'arrow-right': ArrowRight,
  external: ArrowSquareOut,
  spinner: CircleNotch,
  // Sidebar and hub apps
  platform: House,
  workflow: TreeStructure,
  specFiles: FileText,
  specQueue: ListBullets,
  skillsHub: SquaresFour,
  sliders: SlidersHorizontal,
  project: FolderSimple,
  logout: SignOut,
  collapse: SidebarSimple,
  pulse: Pulse,
  quality: ShieldCheck,
  app: Square,
  hand: HandWaving,
  // Content and status
  file: FileText,
  cube: Cube,
  grid: SquaresFour,
  profile: UserCircle,
  notifications: Bell,
  messages: ChatCircle,
  fire: Fire,
  hourglass: Hourglass,
  play: Play,
  pause: Pause,
  swap: ArrowsLeftRight,
  skip: SkipForward,
  cancel: Prohibit,
  warning: Warning,
  github: GithubLogo,
  agent: Hexagon,
  requirement: ClipboardText,
  'test-design': Flask,
  code: Code,
  evidence: Cards,
  handle: DotsSixVertical,
  resize: ArrowsOutSimple,
  reset: ArrowCounterClockwise,
  'arrow-up': ArrowUp,
  'arrow-down': ArrowDown,
  more: DotsThree,
  unknown: Question,
  stop: Stop,
  branch: GitBranch,
  sparkle: Sparkle,
  lightning: Lightning,
  download: DownloadSimple,
  upload: UploadSimple,
  circle: Circle,
  cloud: Cloud,
} as const satisfies Record<string, ComponentType<PhosphorIconProps>>;

export type IconName = keyof typeof ICONS;

/** Whether `name` is a drawable icon; lets callers fall back for names they do not control. */
export function isIconName(name: string): name is IconName {
  return Object.hasOwn(ICONS, name);
}

interface IconProps extends Omit<PhosphorIconProps, 'children'> {
  name: IconName;
}

/**
 * Decorative inline Phosphor icon, sized to the surrounding text (1em) and coloured by
 * `currentColor`. It is hidden from assistive tech: put the meaning in the adjacent label, or an
 * `aria-label` on the parent when the icon stands alone.
 */
export function Icon({ name, className, ...rest }: IconProps) {
  const Glyph = ICONS[name];
  return (
    <Glyph
      aria-hidden="true"
      focusable="false"
      className={cx(styles.icon, name === 'spinner' && styles.spin, className)}
      {...rest}
    />
  );
}
