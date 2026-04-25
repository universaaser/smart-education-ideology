import React, { createContext, ReactNode, useCallback, useContext, useMemo, useRef } from 'react';
import { studentActivityApi, StudentActivityEventRequest } from '../services/api';
import { useAuth } from './AuthContext';

type TrackPayload = Omit<StudentActivityEventRequest, 'occurredAt'>;

interface TrackingContextType {
  track: (event: TrackPayload) => void;
}

const TrackingContext = createContext<TrackingContextType | undefined>(undefined);

export const TrackingProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const { currentUser, roleUi } = useAuth();
  const defaultCourseIdRef = useRef<number>(1);

  const track = useCallback((event: TrackPayload) => {
    if (!currentUser?.id || !roleUi?.capabilities?.canSubmitLearningActivity) {
      return;
    }
    const { courseId: eventCourseId, ...eventBody } = event;
    const courseId = eventCourseId ?? defaultCourseIdRef.current;
    void studentActivityApi.submitEvents({
      studentId: currentUser.id,
      courseId,
      events: [{
        ...eventBody,
        occurredAt: new Date().toISOString(),
      }],
    }).catch(() => {});
  }, [currentUser?.id, roleUi?.capabilities?.canSubmitLearningActivity]);

  const value = useMemo(() => ({ track }), [track]);

  return <TrackingContext.Provider value={value}>{children}</TrackingContext.Provider>;
};

export const useTracking = () => {
  const context = useContext(TrackingContext);
  if (!context) {
    return { track: () => {} };
  }
  return context;
};
