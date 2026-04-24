import React from 'react';
import { Tag } from 'antd';
import {
  TeachingMaterialDraftInfo,
  TeachingMaterialViewInfo,
} from '../services/api';
import { MarkdownView } from './MarkdownView';

type PreviewMaterial = TeachingMaterialDraftInfo | TeachingMaterialViewInfo;

interface TeachingMaterialPreviewProps {
  material: PreviewMaterial;
  sourceFileName?: string;
}

const formatTimestamp = (value?: string) => {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
};

const normalizeStatusColor = (status?: string) => {
  switch (status) {
    case 'PUBLISHED':
      return 'green';
    case 'DRAFT':
      return 'orange';
    default:
      return 'default';
  }
};

export const TeachingMaterialPreview: React.FC<TeachingMaterialPreviewProps> = ({
  material,
  sourceFileName,
}) => {
  const cases = material.cases || [];
  const questions = material.questions || [];
  const traceItems = material.traceItems || [];
  const title = material.title || 'Teaching Material';

  return (
    <article className="teaching-material-preview">
      <header className="teaching-material-preview__header">
        <p className="teaching-material-preview__eyebrow">Teaching Material</p>
        <h1>{title}</h1>
        <div className="teaching-material-preview__meta">
          <span>Version v{material.versionNo || 0}</span>
          <Tag color={normalizeStatusColor(material.status)}>{material.status || 'DRAFT'}</Tag>
          <span>Updated {formatTimestamp(material.updatedAt)}</span>
          {sourceFileName && <span>Source {sourceFileName}</span>}
        </div>
      </header>

      <section className="teaching-material-preview__section">
        <h2>Lecture Notes</h2>
        <MarkdownView
          content={material.lectureNotes}
          emptyText="No lecture notes."
          className="teaching-material-preview__markdown"
        />
      </section>

      <section className="teaching-material-preview__section">
        <h2>Teaching Cases</h2>
        {cases.length > 0 ? (
          <div className="teaching-material-preview__block-list">
            {cases.map((item, index) => (
              <div className="teaching-material-preview__block" key={`preview-case-${index}`}>
                <div className="teaching-material-preview__block-title">Case {index + 1}</div>
                <MarkdownView content={item} emptyText="No case content." compact />
              </div>
            ))}
          </div>
        ) : (
          <div className="teaching-material-preview__empty">No cases.</div>
        )}
      </section>

      <section className="teaching-material-preview__section">
        <h2>Assessment Questions</h2>
        {questions.length > 0 ? (
          <div className="teaching-material-preview__block-list">
            {questions.map((question, index) => (
              <div className="teaching-material-preview__block" key={`preview-question-${index}`}>
                <div className="teaching-material-preview__block-title">
                  Question {index + 1} · {question.questionType || 'SHORT_ANSWER'} · {question.difficulty || 'MEDIUM'}
                </div>
                {question.knowledgePointId && (
                  <div className="teaching-material-preview__field">
                    <span>Knowledge Point ID</span>
                    <p>{question.knowledgePointId}</p>
                  </div>
                )}
                <div className="teaching-material-preview__field">
                  <span>Stem</span>
                  <MarkdownView content={question.stem} emptyText="No question stem." compact />
                </div>
                {(question.options || []).length > 0 && (
                  <div className="teaching-material-preview__field">
                    <span>Options</span>
                    <ol className="teaching-material-preview__point-list">
                      {(question.options || []).map((option, optionIndex) => (
                        <li key={`preview-question-${index}-option-${optionIndex}`}>{option || '-'}</li>
                      ))}
                    </ol>
                  </div>
                )}
                <div className="teaching-material-preview__field">
                  <span>Reference Answer</span>
                  <MarkdownView content={question.referenceAnswer} emptyText="No reference answer." compact />
                </div>
                <div className="teaching-material-preview__field">
                  <span>Scoring Points</span>
                  {(question.scoringPoints || []).length > 0 ? (
                    <ol className="teaching-material-preview__point-list">
                      {(question.scoringPoints || []).map((point, pointIndex) => (
                        <li key={`preview-question-${index}-point-${pointIndex}`}>{point || '-'}</li>
                      ))}
                    </ol>
                  ) : (
                    <div className="teaching-material-preview__empty">No scoring points.</div>
                  )}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="teaching-material-preview__empty">No questions.</div>
        )}
      </section>

      <section className="teaching-material-preview__section teaching-material-preview__section--ideology">
        <h2>Ideology Integration</h2>
        {traceItems.length > 0 ? (
          <div className="teaching-material-preview__trace-grid">
            {traceItems.map((trace, index) => (
              <div className="teaching-material-preview__trace" key={`preview-trace-${index}`}>
                <div className="teaching-material-preview__trace-heading">
                  <Tag color="blue">{trace.knowledgePointName || 'Unknown Point'}</Tag>
                  <Tag color="red">{trace.ideologyElement || 'Unknown Element'}</Tag>
                </div>
                <div className="teaching-material-preview__field">
                  <span>Evidence</span>
                  <p>{trace.evidenceSnippet || '-'}</p>
                </div>
                <div className="teaching-material-preview__field">
                  <span>Integration Reason</span>
                  <p>{trace.matchReason || '-'}</p>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="teaching-material-preview__empty">No ideology integration records.</div>
        )}
      </section>
    </article>
  );
};
