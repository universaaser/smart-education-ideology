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

export const Auth: React.FC = () => {
  const { login } = useAuth();
  const [form] = Form.useForm<FormValues>();
  const [tab, setTab] = useState<TabKey>('login');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const handleTabChange = (value: string) => {
    setTab(value as TabKey);
    setErrorMsg('');
    form.resetFields();
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

      const bootstrapData = await authApi.bootstrap();
      login(bootstrapData);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'The action failed. Please try again.';
      setErrorMsg(message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--bg-light)',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          position: 'absolute',
          top: -96,
          left: -96,
          width: 384,
          height: 384,
          background: 'rgba(22,119,255,0.06)',
          borderRadius: '50%',
          filter: 'blur(60px)',
          pointerEvents: 'none',
        }}
      />
      <div
        style={{
          position: 'absolute',
          top: '50%',
          right: -96,
          width: 256,
          height: 256,
          background: 'rgba(239,68,68,0.05)',
          borderRadius: '50%',
          filter: 'blur(60px)',
          pointerEvents: 'none',
        }}
      />

      <div style={{ width: '100%', maxWidth: 420, padding: '0 20px', position: 'relative', zIndex: 1 }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <div
            style={{
              width: 64,
              height: 64,
              background: 'var(--color-primary)',
              borderRadius: 16,
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 8px 32px rgba(22,119,255,0.4)',
              marginBottom: 16,
            }}
          >
            <span className="material-symbols-outlined" style={{ color: '#fff', fontSize: 32 }}>auto_awesome</span>
          </div>
          <Title level={2} style={{ margin: 0, fontFamily: "'Lexend', sans-serif", fontWeight: 700 }}>
            Smart Ideology Education
          </Title>
          <Text type="secondary" style={{ fontSize: 13 }}>Teaching support platform · Account access</Text>
        </div>

        <Card
          style={{ borderRadius: 20, boxShadow: '0 8px 40px rgba(0,0,0,0.08)', border: '1px solid var(--color-border)' }}
          bodyStyle={{ padding: '28px 32px 24px' }}
        >
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 24 }}>
            <Segmented
              options={[
                { label: 'Login', value: 'login' },
                { label: 'Register', value: 'register' },
              ]}
              value={tab}
              onChange={handleTabChange}
              block
              style={{ width: '100%' }}
            />
          </div>

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
            {tab === 'register' && (
              <>
                <Form.Item name="role">
                  <Radio.Group style={{ width: '100%', marginBottom: 4 }}>
                    <Space style={{ width: '100%' }}>
                      <Radio.Button
                        value="TEACHER"
                        style={{ flex: 1, textAlign: 'center', borderRadius: 8 }}
                      >
                        <BookOutlined /> Teacher
                      </Radio.Button>
                      <Radio.Button
                        value="STUDENT"
                        style={{ flex: 1, textAlign: 'center', borderRadius: 8 }}
                      >
                        <ReadOutlined /> Student
                      </Radio.Button>
                    </Space>
                  </Radio.Group>
                </Form.Item>

                <Form.Item
                  shouldUpdate={(previous, current) => previous.role !== current.role}
                  noStyle
                >
                  {({ getFieldValue }) => (
                    <Form.Item
                      label={getFieldValue('role') === 'TEACHER' ? 'Work Email' : 'Contact Email'}
                      name="email"
                      rules={[
                        { required: true, message: 'Please enter an email address' },
                        { type: 'email', message: 'Please enter a valid email address' },
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

            <Form.Item
              label={
                <Form.Item shouldUpdate={(previous, current) => previous.role !== current.role} noStyle>
                  {({ getFieldValue }) =>
                    getFieldValue('role') === 'TEACHER' ? 'Username / Staff ID' : 'Username / Student ID'
                  }
                </Form.Item>
              }
              name="username"
              rules={[{ required: true, message: 'Please enter a username' }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: 'var(--color-text-tertiary)' }} />}
                placeholder="Enter your account"
                size="large"
              />
            </Form.Item>

            <Form.Item
              label={
                <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                  <span>Password</span>
                  {tab === 'login' && (
                    <Button type="link" size="small" style={{ padding: 0, height: 'auto', fontSize: 12 }}>
                      Forgot password?
                    </Button>
                  )}
                </div>
              }
              name="password"
              rules={[{ required: true, message: 'Please enter a password' }]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: 'var(--color-text-tertiary)' }} />}
                placeholder="Enter your password"
                size="large"
              />
            </Form.Item>

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
                  height: 48,
                  borderRadius: 12,
                  fontWeight: 700,
                  fontSize: 15,
                  boxShadow: '0 4px 16px rgba(22,119,255,0.35)',
                }}
              >
                {tab === 'login' ? 'Sign In' : 'Create Account'}
              </Button>
            </Form.Item>
          </Form>
        </Card>

        <Divider style={{ margin: '20px 0 0' }}>
          <Text type="secondary" style={{ fontSize: 11 }}>
            By continuing, you agree to the
            <Button type="link" size="small" style={{ padding: '0 2px', fontSize: 11 }}>Terms of Service</Button>
            and the
            <Button type="link" size="small" style={{ padding: '0 2px', fontSize: 11 }}>Privacy Policy</Button>
          </Text>
        </Divider>
      </div>
    </div>
  );
};
