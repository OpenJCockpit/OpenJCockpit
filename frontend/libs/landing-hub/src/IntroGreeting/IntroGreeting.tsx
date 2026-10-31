import { useEffect } from 'react';
import { useTranslation } from '@openjcockpit/i18n';
import { DecipherText, FrameOctagon, Reveal, cx } from '@openjcockpit/ui';
import styles from './IntroGreeting.module.scss';

type IntroGreetingProps = { firstName: string; onDone: () => void };

export function IntroGreeting({ firstName, onDone }: IntroGreetingProps) {
  const { t } = useTranslation('landing');
  const greeting = t('intro.greeting', { firstName });
  useEffect(() => {
    const timer = window.setTimeout(onDone, 2850);
    return () => window.clearTimeout(timer);
  }, [onDone]);

  return (
    <section className={styles.introScreen} aria-label={greeting} data-testid="intro-greeting">
      <div className={cx(styles.introRadar, styles.introRadarOne)} />
      <div className={cx(styles.introRadar, styles.introRadarTwo)} />
      <Reveal as="div" className={styles.introFrame} duration={1.2} from={{ scale: 0.88 }}>
        <FrameOctagon className={styles.introOctagon} duration={1.2} />
        <DecipherText as="h1" className={styles.introTitle} text={greeting} durationMs={1200} />
      </Reveal>
    </section>
  );
}
