import { useEffect } from 'react';
import Reveal from '../../ui/motion/Reveal';
import FrameOctagon from '../../ui/frames/FrameOctagon';
import DecipherText from '../../ui/vendor/react-bits/DecipherText';
import { cx } from '../../ui/cx';
import styles from './IntroGreeting.module.scss';

type IntroGreetingProps = { firstName: string; onDone: () => void };

export default function IntroGreeting({ firstName, onDone }: IntroGreetingProps) {
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
