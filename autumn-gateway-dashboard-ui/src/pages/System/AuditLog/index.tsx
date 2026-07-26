import { ProTable } from '@ant-design/pro-components';
import { Tag } from 'antd';
import type { ProColumns, ActionType } from '@ant-design/pro-components';
import { useRef, useState, useEffect, useMemo } from 'react';
import { request } from '@umijs/max';

type AuditLog = {
    id: number;
    appId?: number;
    userId?: number;
    action?: string;
    resource?: string;
    method?: string;
    status: number;
    reason?: string;
    clientIp?: string;
    userAgent?: string;
    requestId?: string;
    createdAt: string;
};

const ACTIONS = ['LOGIN', 'ACCESS_CHECK', 'PERMISSION_CHANGE', 'ROLE_ASSIGN'];

export default () => {
    const actionRef = useRef<ActionType>();
    const [applications, setApplications] = useState<any[]>([]);

    useEffect(() => {
        request<{ data: any[] }>('/api/system/app/list').then(res => {
            setApplications(res.data || []);
        });
    }, []);

    const appOptions = useMemo(() =>
        applications.map(app => ({ label: `${app.name} (${app.appid})`, value: app.id })),
        [applications]
    );

    const columns: ProColumns<AuditLog>[] = [
        { title: 'ID', dataIndex: 'id', search: false, width: 70 },
        {
            title: '应用',
            dataIndex: 'appId',
            valueType: 'select',
            fieldProps: { options: appOptions, allowClear: true, placeholder: '全部' },
            render: (_, record) => {
                const app = applications.find(a => a.id === record.appId);
                return app ? `${app.name} (${app.appid})` : '-';
            },
        },
        {
            title: '用户ID',
            dataIndex: 'userId',
            search: true,
            width: 80,
        },
        {
            title: '操作类型',
            dataIndex: 'action',
            valueType: 'select',
            fieldProps: { options: ACTIONS.map(a => ({ label: a, value: a })), allowClear: true },
            render: (_, record) => {
                const colors: Record<string, string> = {
                    LOGIN: 'blue',
                    ACCESS_CHECK: 'green',
                    PERMISSION_CHANGE: 'orange',
                    ROLE_ASSIGN: 'purple',
                };
                return <Tag color={colors[record.action || ''] || 'default'}>{record.action || '-'}</Tag>;
            },
        },
        { title: '资源', dataIndex: 'resource', ellipsis: true, search: false },
        { title: '方法', dataIndex: 'method', search: false, width: 70 },
        {
            title: '结果',
            dataIndex: 'status',
            valueType: 'select',
            fieldProps: { options: [{ label: '允许', value: 1 }, { label: '拒绝', value: 0 }], allowClear: true },
            render: (_, record) => (
                <Tag color={record.status === 1 ? 'green' : 'red'}>
                    {record.status === 1 ? '允许' : '拒绝'}
                </Tag>
            ),
        },
        { title: '原因', dataIndex: 'reason', ellipsis: true, search: false },
        { title: '客户端IP', dataIndex: 'clientIp', search: false, width: 130 },
        { title: '请求ID', dataIndex: 'requestId', copyable: true, search: false, width: 120, ellipsis: true },
        { title: '时间', dataIndex: 'createdAt', valueType: 'dateTime', search: false, width: 170 },
    ];

    return (
        <ProTable<AuditLog>
            headerTitle="审计日志"
            actionRef={actionRef}
            rowKey="id"
            request={async (params) => {
                const res = await request<{ data: AuditLog[]; total: number }>('/api/system/audit-log', {
                    params: {
                        current: params.current,
                        pageSize: params.pageSize,
                        appId: params.appId,
                        userId: params.userId,
                        action: params.action,
                        status: params.status,
                        startTime: params.startTime,
                        endTime: params.endTime,
                    },
                });
                return { data: res.data, success: true, total: res.total };
            }}
            columns={columns}
            search={{ labelWidth: 'auto' }}
            toolbar={false}
        />
    );
};
