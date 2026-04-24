import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Divider, Empty, List, Space, Tag, Typography } from 'antd';
import { CopyOutlined, PlusOutlined, SafetyCertificateOutlined, WarningOutlined, CompassOutlined } from '@ant-design/icons';
import { SelectionExplainHistoryInfo, SelectionExplainResponse, semanticApi, SemanticHit } from '../services/api';
import { MarkdownView } from './MarkdownView';

const { Text } = Typography;

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
  const [similarHits, setSimilarHits] = useState<SemanticHit[]>([]);
  const [similarLoading, setSimilarLoading] = useState(false);

  useEffect(() => {
    if (!selectionExplainResult?.answer) {
      setSimilarHits([]);
      return;
    }
    setSimilarLoading(true);
    semanticApi.search({
      scope: 'selection_explain',
      text: selectionExplainResult.answer,
      topK: 3,
    }).then(hits => {
      setSimilarHits(hits);
    }).catch(() => {
      setSimilarHits([]);
    }).finally(() => {
      setSimilarLoading(false);
    });
  }, [selectionExplainResult?.answer]);

  if (!selectionExplainResult && selectionExplainHistory.length === 0) {
    return null;
  }

  return (
    <Card
      size="small"
      style={{
        borderRadius: 12,
        border: '1px solid #e2e8f0',
        background: 'linear-gradient(180deg, #fafbff 0%, #ffffff 60%)',
      }}
      bodyStyle={{ padding: 18 }}
    >
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {selectionExplainResult && (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Space style={{ width: '100%', justifyContent: 'space-between' }} align="center">
              <Space size={8} align="center">
                <SafetyCertificateOutlined style={{ color: '#1677ff', fontSize: 16 }} />
                <Text strong style={{ fontSize: 15 }}>Selection Explanation</Text>
                {selectionExplainResult.hasReliableEvidence ? (
                  <Tag color="green" style={{ marginInlineEnd: 0 }}>Evidence</Tag>
                ) : (
                  <Tag color="orange" style={{ marginInlineEnd: 0 }}>No Evidence</Tag>
                )}
              </Space>
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
                  icon={<PlusOutlined />}
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
                icon={<WarningOutlined />}
                message="No reliable knowledge-base evidence found"
                description="The explanation is generated with general model knowledge. Please verify before use."
                style={{ borderRadius: 8 }}
              />
            )}
            <div
              style={{
                background: '#ffffff',
                border: '1px solid #e2e8f0',
                borderRadius: 10,
                padding: '14px 18px',
                boxShadow: '0 1px 2px rgba(15, 23, 42, 0.03)',
              }}
            >
              <MarkdownView content={selectionExplainResult.answer} />
            </div>
            <Text strong style={{ fontSize: 13 }}>Knowledge Evidence</Text>
            <Space direction="vertical" size={8} style={{ width: '100%' }}>
              {(selectionExplainResult.evidenceItems || []).length > 0 ? (
                (selectionExplainResult.evidenceItems || []).map((item, index) => (
                  <div
                    key={`selection-evidence-${item.evidenceType}-${item.referenceId || index}`}
                    style={{
                      background: '#f8fafc',
                      border: '1px solid #e2e8f0',
                      borderRadius: 8,
                      padding: '10px 14px',
                    }}
                  >
                    <Space direction="vertical" size={4} style={{ width: '100%' }}>
                      <Space wrap size={6}>
                        <Tag color="blue" style={{ marginInlineEnd: 0 }}>
                          {item.evidenceType || 'EVIDENCE'}
                        </Tag>
                        <Text strong>{item.title || 'Untitled'}</Text>
                      </Space>
                      <Text type="secondary" style={{ fontSize: 13 }}>{item.summary || '-'}</Text>
                      {(item.source || item.sourceUrl) && (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {item.source || 'Source'}
                          {item.sourceUrl ? ` | ${item.sourceUrl}` : ''}
                        </Text>
                      )}
                    </Space>
                  </div>
                ))
              ) : (
                <Text type="secondary">No evidence items.</Text>
              )}
            </Space>
          </Space>
        )}

        {similarHits.length > 0 && (
          <>
            <Divider style={{ margin: '4px 0' }} />
            <Space style={{ width: '100%', justifyContent: 'space-between' }}>
              <Text strong style={{ fontSize: 14 }}>
                <CompassOutlined style={{ color: '#1677ff', marginRight: 6 }} />
                Similar Recommendations
              </Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                Semantic match
              </Text>
            </Space>
            <List
              size="small"
              loading={similarLoading}
              dataSource={similarHits}
              renderItem={item => (
                <List.Item>
                  <List.Item.Meta
                    title={
                      <Space>
                        <Text strong>{item.title || 'Untitled'}</Text>
                        <Tag color="blue">{(item.score ?? 0).toFixed(3)}</Tag>
                      </Space>
                    }
                    description={
                      <Text type="secondary" style={{ fontSize: 13 }}>
                        {item.snippet || '-'}
                      </Text>
                    }
                  />
                </List.Item>
              )}
            />
          </>
        )}

        <Divider style={{ margin: '4px 0' }} />
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Text strong style={{ fontSize: 14 }}>Explanation History</Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Latest {selectionExplainHistory.length} records
          </Text>
        </Space>
        <List
          loading={selectionHistoryLoading}
          dataSource={selectionExplainHistory}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="No explanation history"
              />
            ),
          }}
          renderItem={(item) => (
            <List.Item
              style={{ alignItems: 'flex-start', padding: '12px 4px' }}
              actions={[
                <Button
                  key="copy"
                  size="small"
                  icon={<CopyOutlined />}
                  onClick={() => onCopySelectionExplanation(item.answer)}
                >
                  Copy
                </Button>,
                <Button
                  key="append"
                  size="small"
                  type="link"
                  onClick={() => onAppendSelectionExplanation(item.answer)}
                >
                  Append
                </Button>,
              ]}
            >
              <List.Item.Meta
                title={(
                  <Space wrap size={6}>
                    <Text ellipsis style={{ maxWidth: 320 }}>
                      {item.selectedText || 'Selected text'}
                    </Text>
                    {item.hasReliableEvidence ? (
                      <Tag color="green" style={{ marginInlineEnd: 0 }}>Evidence</Tag>
                    ) : (
                      <Tag color="orange" style={{ marginInlineEnd: 0 }}>No Evidence</Tag>
                    )}
                  </Space>
                )}
                description={(
                  <div style={{ marginTop: 2 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      {item.createdAt || '-'}
                    </Text>
                    <MarkdownView
                      compact
                      content={truncate(item.answer, 260)}
                      style={{ marginTop: 4 }}
                    />
                  </div>
                )}
              />
            </List.Item>
          )}
        />
      </Space>
    </Card>
  );
};

/**
 * 截取用于历史列表预览的回答内容；超过限制时追加省略号。
 * 这里保留原文前 N 个字符即可，Markdown 渲染器能容忍截断。
 */
function truncate(value: string | undefined | null, max: number): string {
  const text = (value || '').trim();
  if (text.length <= max) {
    return text;
  }
  return `${text.slice(0, max)}...`;
}
