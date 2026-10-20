import { useEffect } from 'react';
import { DecipherText, FrameOctagon, Reveal, cx } from '@openjcockpit/ui';
import styles from './IntroGreeting.module.scss';

type IntroGreetingProps = { firstName: string; onDone: () => void };

export function IntroGreeting({ firstName, onDone }: IntroGreetingProps) {
  useEffect(() => {
    const timer = window.setTimeout(onDone, 2850);
    return () => window.clearTimeout(timer);
  }, [onDone]);

  return (
    <section
      className={styles.introScreen}
      aria-label={`Hi ${firstName}!`}
      data-testid="intro-greeting"
    >
      <div className={cx(styles.introRadar, styles.introRadarOne)} />
      <div className={cx(styles.introRadar, styles.introRadarTwo)} />
      <Reveal as="div" className={styles.introFrame} duration={1.2} from={{ scale: 0.88 }}>
        <FrameOctagon className={styles.introOctagon} duration={1.2} />
        <DecipherText
          as="h1"
          className={styles.introTitle}
          text={`Hi ${firstName}!`}
          durationMs={1200}
        />
      </Reveal>
    </section>
  );
}
