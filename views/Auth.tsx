import React, { useState } from 'react';
import {
  Form, Input, Button, Alert, Card, Segmented, Radio,
  Typography, Space, Divider,
} from 'antd';
import {
  UserOutlined, LockOutlined, MailOutlined,
  BookOutlined, ReadOutlined, ArrowRightOutlined,
} from '@ant-design/icons';
import { authApi, setToken } from '../services/api';
import { useAuth } from '../contexts/AuthContext';

const { Title, Text } = Typography;

type TabKey = 'login' | 'register';
type RoleKey = 'TEACHER' | 'STUDENT';

interface FormValues {
  username: string;
  password: string;
  email?: string;
  role: RoleKey;
}

/**
 * 登录 / 注册页
 *
 * NOTE: 使用 Ant Design Form 替换手写 form，享受表单校验和字段联动能力。
 * Segmented 替换登录/注册切换按钮，Radio.Group 替换角色选择按钮。
 */
export const Auth: React.FC = () => {
  const { login } = useAuth();
  const [form] = Form.useForm<FormValues>();
  const [tab, setTab] = useState<TabKey>('login');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  /** 切换登录/注册 Tab 时重置表单和错误 */
  const handleTabChange = (val: string) => {
    setTab(val as TabKey);
    setErrorMsg('');
    form.resetFields();
    // 注册时默认角色为 TEACHER
    form.setFieldValue('role', 'TEACHER');
  };

  const handleSubmit = async (values: FormValues) => {
    setIsLoading(true);
    setErrorMsg('');

    try {
      if (tab === 'login') {
        const result = await authApi.login(values.username, values.password);
        setToken(result.token);
      } else {
        const result = await authApi.register(
          values.username,
          values.password,
          values.email || '',
          values.role,
        );
        setToken(result.token);
      }

      // 成功获取 token 后通过 bootstrap 接口拉取完整用户数据
      const bootstrapData = await authApi.bootstrap();
      login(bootstrapData);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '操作失败，请重试';
      setErrorMsg(message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--bg-light)',
      position: 'relative',
      overflow: 'hidden',
    }}>
      {/* 背景装饰光晕 */}
      <div style={{
        position: 'absolute', top: -96, left: -96,
        width: 384, height: 384,
        background: 'rgba(22,119,255,0.06)', borderRadius: '50%', filter: 'blur(60px)',
        pointerEvents: 'none',
      }} />
      <div style={{
        position: 'absolute', top: '50%', right: -96,
        width: 256, height: 256,
        background: 'rgba(239,68,68,0.05)', borderRadius: '50%', filter: 'blur(60px)',
        pointerEvents: 'none',
      }} />

      <div style={{ width: '100%', maxWidth: 420, padding: '0 20px', position: 'relative', zIndex: 1 }}>
        {/* Logo 区 */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div style={{
            width: 64, height: 64,
            background: 'var(--color-primary)',
            borderRadius: 16,
            display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            boxShadow: '0 8px 32px rgba(22,119,255,0.4)',
            marginBottom: 16,
          }}>
            <span className="material-symbols-outlined" style={{ color: '#fff', fontSize: 32 }}>auto_awesome</span>
          </div>
          <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif", fontWeight: 700 }}>
            智教思政
          </Title>
          <Text type="secondary" style={{ fontSize: 13 }}>智慧教学辅助系统 · 登录入口</Text>
        </div>

        <Card
          style={{ borderRadius: 20, boxShadow: '0 8px 40px rgba(0,0,0,0.08)', border: '1px solid var(--color-border)' }}
          bodyStyle={{ padding: '28px 32px 24px' }}
        >
          {/* 登录/注册切换 */}
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 24 }}>
            <Segmented
              options={[
                { label: '登  录', value: 'login' },
                { label: '注  册', value: 'register' },
              ]}
              value={tab}
              onChange={handleTabChange}
              block
              style={{ width: '100%' }}
            />
          </div>

          {/* 错误提示 */}
          {errorMsg && (
            <Alert
              type="error"
              message={errorMsg}
              showIcon
              style={{ marginBottom: 20, borderRadius: 8 }}
            />
          )}

          <Form
            form={form}
            layout="vertical"
            onFinish={handleSubmit}
            initialValues={{ role: 'TEACHER' }}
            requiredMark={false}
          >
            {/* 注册专属：角色选择 + 邮箱 */}
            {tab === 'register' && (
              <>
                <Form.Item name="role">
                  <Radio.Group style={{ width: '100%', marginBottom: 4 }}>
                    <Space style={{ width: '100%' }}>
                      <Radio.Button
                        value="TEACHER"
                        style={{ flex: 1, textAlign: 'center', borderRadius: 8 }}
                      >
                        <BookOutlined /> 教师注册
                      </Radio.Button>
                      <Radio.Button
                        value="STUDENT"
                        style={{ flex: 1, textAlign: 'center', borderRadius: 8 }}
                      >
                        <ReadOutlined /> 学生注册
                      </Radio.Button>
                    </Space>
                  </Radio.Group>
                </Form.Item>

                {/* 动态 label：教师用"教工邮箱"，学生用"联系邮箱" */}
                <Form.Item
                  shouldUpdate={(prev, curr) => prev.role !== curr.role}
                  noStyle
                >
                  {({ getFieldValue }) => (
                    <Form.Item
                      label={getFieldValue('role') === 'TEACHER' ? '教工邮箱' : '联系邮箱'}
                      name="email"
                      rules={[
                        { required: true, message: '请输入邮箱' },
                        { type: 'email', message: '邮箱格式不正确' },
                      ]}
                    >
                      <Input
                        prefix={<MailOutlined style={{ color: 'var(--color-text-tertiary)' }} />}
                        placeholder={
                          getFieldValue('role') === 'TEACHER'
                            ? 'example@university.edu.cn'
                            : 'student@stu.university.edu.cn'
                        }
                        size="large"
                      />
                    </Form.Item>
                  )}
                </Form.Item>
              </>
            )}

            {/* 用户名 */}
            <Form.Item
              label={
                <Form.Item shouldUpdate={(p, c) => p.role !== c.role} noStyle>
                  {({ getFieldValue }) =>
                    getFieldValue('role') === 'TEACHER' ? '用户名 / 教工号' : '用户名 / 学号'
                  }
                </Form.Item>
              }
              name="username"
              rules={[{ required: true, message: '请输入用户名' }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: 'var(--color-text-tertiary)' }} />}
                placeholder="请输入您的账号"
                size="large"
              />
            </Form.Item>

            {/* 密码 */}
            <Form.Item
              label={
                <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                  <span>密码</span>
                  {tab === 'login' && (
                    <Button type="link" size="small" style={{ padding: 0, height: 'auto', fontSize: 12 }}>
                      忘记密码?
                    </Button>
                  )}
                </div>
              }
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: 'var(--color-text-tertiary)' }} />}
                placeholder="••••••••"
                size="large"
              />
            </Form.Item>

            {/* 提交按钮 */}
            <Form.Item style={{ marginTop: 8, marginBottom: 0 }}>
              <Button
                type="primary"
                htmlType="submit"
                loading={isLoading}
                block
                size="large"
                icon={!isLoading ? <ArrowRightOutlined /> : undefined}
                iconPosition="end"
                style={{
                  height: 48, borderRadius: 12, fontWeight: 700, fontSize: 15,
                  boxShadow: '0 4px 16px rgba(22,119,255,0.35)',
                }}
              >
                {tab === 'login' ? '立即登录' : '创建账户'}
              </Button>
            </Form.Item>
          </Form>
        </Card>

        <Divider style={{ margin: '20px 0 0' }}>
          <Text type="secondary" style={{ fontSize: 11 }}>
            登录即代表同意
            <Button type="link" size="small" style={{ padding: '0 2px', fontSize: 11 }}>《服务协议》</Button>
            与
            <Button type="link" size="small" style={{ padding: '0 2px', fontSize: 11 }}>《隐私政策》</Button>
          </Text>
        </Divider>
      </div>
    </div>
  );
};
