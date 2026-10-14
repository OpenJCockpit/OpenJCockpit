interface Props {
  messages: (string | null | undefined)[];
}

export function RunStatusAnnouncements({ messages }: Props) {
  return (
    <>
      {messages.map(
        (message, index) =>
          message && (
            <p
              key={index}
              aria-live="polite"
              role="status"
              className="workflow-waiting-announcement"
            >
              {message}
            </p>
          ),
      )}
    </>
  );
}
