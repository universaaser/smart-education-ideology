import React from 'react';
import { Button, Popconfirm, Select, Space, Tag, Typography } from 'antd';
import { DeleteOutlined } from '@ant-design/icons';
import { KnowledgeNodeInfo, KnowledgeRelationInfo } from '../services/api';

const { Text } = Typography;

interface KnowledgeRelationListProps {
  selectedNode: KnowledgeNodeInfo;
  nodes: KnowledgeNodeInfo[];
  relations: KnowledgeRelationInfo[];
  canEdit: boolean;
  editingRelationId: number | null;
  deletingRelationId: number | null;
  relationTypes: string[];
  getNodeColor: (nodeType: string) => { border: string };
  getRelationColor: (relationType: string) => string;
  onSelectNode: (node: KnowledgeNodeInfo) => void;
  onUpdateRelation: (connection: KnowledgeRelationInfo, relationType: string) => void;
  onDeleteRelation: (connection: KnowledgeRelationInfo) => void;
}

export const KnowledgeRelationList: React.FC<KnowledgeRelationListProps> = ({
  selectedNode,
  nodes,
  relations,
  canEdit,
  editingRelationId,
  deletingRelationId,
  relationTypes,
  getNodeColor,
  getRelationColor,
  onSelectNode,
  onUpdateRelation,
  onDeleteRelation,
}) => (
  <div>
    <Text type="secondary" style={{ fontSize: 12, fontWeight: 700, textTransform: 'uppercase', marginBottom: 8, display: 'block' }}>Relations</Text>
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {relations.map(connection => {
        const linkedId = connection.fromNodeId === selectedNode.id ? connection.toNodeId : connection.fromNodeId;
        const linkedNode = nodes.find(node => node.id === linkedId);
        if (!linkedNode) {
          return null;
        }
        const isSynthetic = connection.id < 0;
        const direction = connection.fromNodeId === selectedNode.id ? 'Outgoing' : 'Incoming';
        return (
          <div
            key={connection.id}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 8,
              padding: '10px 12px',
              border: '1px solid #f0f0f0',
              borderRadius: 8,
              background: '#fafafa',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <div style={{ width: 10, height: 10, borderRadius: '50%', background: getNodeColor(linkedNode.nodeType).border }} />
              <Text style={{ fontSize: 14, flex: 1, cursor: 'pointer' }} ellipsis onClick={() => onSelectNode(linkedNode)}>
                {linkedNode.name || `Node #${linkedId}`}
              </Text>
              <Tag style={{ margin: 0 }}>{direction}</Tag>
              {isSynthetic && <Tag color="blue" style={{ margin: 0 }}>Readonly</Tag>}
            </div>
            {canEdit && !isSynthetic ? (
              <Space.Compact style={{ width: '100%' }}>
                <Select
                  value={connection.relationType}
                  options={relationTypes.map(type => ({ label: type, value: type }))}
                  loading={editingRelationId === connection.id}
                  onChange={value => onUpdateRelation(connection, value)}
                  style={{ flex: 1 }}
                />
                <Popconfirm
                  title="Delete this relation?"
                  okText="Delete"
                  cancelText="Cancel"
                  okButtonProps={{ danger: true, loading: deletingRelationId === connection.id }}
                  onConfirm={() => onDeleteRelation(connection)}
                >
                  <Button danger icon={<DeleteOutlined />} loading={deletingRelationId === connection.id} />
                </Popconfirm>
              </Space.Compact>
            ) : (
              <Tag color="default" style={{ alignSelf: 'flex-start', margin: 0, border: 'none', background: `${getRelationColor(connection.relationType)}20`, color: getRelationColor(connection.relationType) }}>
                {connection.relationType}
              </Tag>
            )}
            {connection.description && <Text type="secondary" style={{ fontSize: 12 }}>{connection.description}</Text>}
          </div>
        );
      })}
      {relations.length === 0 && (
        <Text type="secondary" style={{ fontSize: 13 }}>No linked nodes</Text>
      )}
    </div>
  </div>
);
