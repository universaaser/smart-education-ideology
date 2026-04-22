import React from 'react';
import { Alert, Button, Card, Divider, List, Space, Tag, Typography } from 'antd';
import { CopyOutlined } from '@ant-design/icons';
import { SelectionExplainHistoryInfo, SelectionExplainResponse } from '../services/api';

const { Paragraph, Text } = Typography;

interface SelectionExplainPanelProps {
  selectionExplainResult: SelectionExplainResponse | null;
  selectionExplainHistory: SelectionExplainHistoryInfo[];
  selectionHistoryLoading: boolean;
  onCopySelectionExplanation: (answer: string) => void;
  onAppendSelectionExplanation: (answer: string) => void;
}

export const SelectionExplainPanel: React.FC<SelectionExplainPanelProps> = ({
  selectionExplainResult,
  selectionExplainHistory,
  selectionHistoryLoading,
  onCopySelectionExplanation,
  onAppendSelectionExplanation,
}) => {
  if (!selectionExplainResult && selectionExplainHistory.length === 0) {
    return null;
  }

  return (
    <Card size="small" style={{ borderRadius: 8 }}>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {selectionExplainResult && (
          <Space direction="vertical" size="small" style={{ width: '100%' }}>
            <Space style={{ width: '100%', justifyContent: 'space-between' }}>
              <Text strong>Selection Explanation</Text>
              <Space>
                <Button
                  size="small"
                  icon={<CopyOutlined />}
                  onClick={() => onCopySelectionExplanation(selectionExplainResult.answer)}
                >
                  Copy
                </Button>
                <Button
                  size="small"
                  type="primary"
                  onClick={() => onAppendSelectionExplanation(selectionExplainResult.answer)}
                >
                  Append to Notes
                </Button>
              </Space>
            </Space>
            {!selectionExplainResult.hasReliableEvidence && (
              <Alert
                type="warning"
                showIcon
                message="No reliable knowledge-base evidence found"
              />
            )}
            <Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
              {selectionExplainResult.answer}
            </Paragraph>
            <Text strong>Knowledge Evidence</Text>
            <Space direction="vertical" style={{ width: '100%' }}>
              {(selectionExplainResult.evidenceItems || []).length > 0 ? (
                (selectionExplainResult.evidenceItems || []).map((item, index) => (
                  <Alert
                    key={`selection-evidence-${item.evidenceType}-${item.referenceId || index}`}
                    type="info"
                    showIcon={false}
                    message={(
                      <Space direction="vertical" size={2}>
                        <Space wrap>
                          <Tag color="blue">{item.evidenceType || 'EVIDENCE'}</Tag>
                          <Text strong>{item.title || 'Untitled'}</Text>
                        </Space>
                        <Text type="secondary">{item.summary || '-'}</Text>
                        {(item.source || item.sourceUrl) && (
                          <Text style={{ fontSize: 12 }}>
                            {item.source || 'Source'} {item.sourceUrl ? `| ${item.sourceUrl}` : ''}
                          </Text>
                        )}
                      </Space>
                    )}
                  />
                ))
              ) : (
                <Text type="secondary">No evidence items.</Text>
              )}
            </Space>
          </Space>
        )}

        <Divider style={{ margin: '4px 0' }} />
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Text strong>Explanation History</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Latest {selectionExplainHistory.length} records
          </Text>
        </Space>
        <List
          loading={selectionHistoryLoading}
          dataSource={selectionExplainHistory}
          locale={{ emptyText: 'No explanation history.' }}
          renderItem={(item) => (
            <List.Item
              actions={[
                <Button
                  key="copy"
                  size="small"
                  onClick={() => onCopySelectionExplanation(item.answer)}
                >
                  Copy
                </Button>,
                <Button
                  key="append"
                  size="small"
                  onClick={() => onAppendSelectionExplanation(item.answer)}
                >
                  Append
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={(
                  <Space wrap>
                    <Text>{item.selectedText || 'Selected text'}</Text>
                    {item.hasReliableEvidence ? (
                      <Tag color="green">Evidence</Tag>
                    ) : (
                      <Tag color="orange">No Evidence</Tag>
                    )}
                  </Space>
                )}
                description={(
                  <Space direction="vertical" size={2}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {item.createdAt || '-'}
                    </Text>
                    <Text style={{ whiteSpace: 'pre-wrap' }}>
                      {(item.answer || '').slice(0, 240)}
                      {(item.answer || '').length > 240 ? '...' : ''}
                    </Text>
                  </Space>
                )}
              />
            </List.Item>
          )}
        />
      </Space>
    </Card>
  );
};
