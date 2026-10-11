import React, { useCallback, useEffect, useState } from "react";
import { ApiErrorException, NetworkError, getText } from "../api/client";

type HealthStatus = "loading" | "success" | "error";

interface HealthState {
  status: HealthStatus;
  message?: string;
  detail?: string;
}

function describeError(error: unknown): HealthState {
  if (error instanceof ApiErrorException) {
    const { code, status, correlationId } = error.apiError;
    const parts = [`Error code: ${code}`, `HTTP ${status}`];
    if (correlationId) {
      parts.push(`Correlation ID: ${correlationId}`);
    }
    return {
      status: "error",
      message: error.apiError.message,
      detail: parts.join(" · "),
    };
  }

  if (error instanceof NetworkError) {
    return {
      status: "error",
      message: error.message,
      detail: "Make sure the ECHO backend is running and reachable.",
    };
  }

  return {
    status: "error",
    message: "An unexpected error occurred while contacting the ECHO backend.",
  };
}

const HealthCheck: React.FC = () => {
  const [state, setState] = useState<HealthState>({ status: "loading" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    setState({ status: "loading" });

    getText("/health").then(
      (message) => {
        if (active) {
          setState({ status: "success", message });
        }
      },
      (error: unknown) => {
        if (active) {
          setState(describeError(error));
        }
      }
    );

    return () => {
      active = false;
    };
  }, [attempt]);

  const retry = useCallback(() => {
    setAttempt((current) => current + 1);
  }, []);

  return (
    <div
      className={`health-check health-check--${state.status}`}
      role={state.status === "error" ? "alert" : "status"}
      aria-live={state.status === "error" ? "assertive" : "polite"}
    >
      <span className="health-check__indicator" aria-hidden="true" />

      <div className="health-check__body">
        <p className="health-check__label">Backend</p>

        {state.status === "loading" && (
          <p className="health-check__message">Checking ECHO backend…</p>
        )}

        {state.status === "success" && (
          <p className="health-check__message">
            {state.message ?? "ECHO backend is running"}
          </p>
        )}

        {state.status === "error" && (
          <>
            <p className="health-check__message">{state.message}</p>
            {state.detail && (
              <p className="health-check__detail">{state.detail}</p>
            )}
          </>
        )}
      </div>

      {state.status === "error" && (
        <button type="button" className="health-check__retry" onClick={retry}>
          Retry
        </button>
      )}
    </div>
  );
};

export default HealthCheck;
