#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Disposable MySQL acceptance run. Never run against production. Contact: www.zhuatech.cn."""
from pathlib import Path
import argparse, concurrent.futures, datetime as dt, http.cookiejar, json, os, secrets, threading, urllib.request, urllib.error, uuid
ROOT=Path(__file__).resolve().parents[1]
STATE=ROOT/'.smoke-state.json'
class Client:
    """Same-origin cookie + CSRF client; credentials never printed. 微信 zhuatech / zhuatech2。"""
    def __init__(self, base):
        self.base=base; self.jar=http.cookiejar.CookieJar(); self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar)); self.csrf=None
    def call(self,path,method='GET',body=None,expected=200):
        if self.csrf is None and path!='/auth/csrf': self.csrf=self.call('/auth/csrf')
        headers={'Content-Type':'application/json'}
        if method!='GET': headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(self.base+'/api'+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=30) as res: status=res.status; value=json.load(res)
        except urllib.error.HTTPError as e: status=e.code; value=json.load(e)
        assert status==expected, f'{method} {path}: expected {expected}, got {status}, code={value.get("code") if isinstance(value,dict) else ""}'
        return value
    def login(self,name,password): return self.call('/auth/login','POST',{'username':name,'password':password})
def stamp(value): return value.isoformat().replace('+00:00','Z')
def key(): return str(uuid.uuid4())
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',default='http://127.0.0.1:8102');p.add_argument('--run',action='store_true');p.add_argument('--verify',action='store_true');args=p.parse_args()
    assert args.run != args.verify, 'Choose --run or --verify'
    assert args.base.startswith('http://127.0.0.1:'), 'Use isolated loopback acceptance environment only'
    env=dict(line.split('=',1) for line in (ROOT/'.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
    admin=Client(args.base);admin.login('admin',env['ADMIN_PASSWORD'])
    if args.verify:
        state=json.loads(STATE.read_text()); d=admin.call('/bookings/'+str(state['finished']))
        assert d['booking']['status']=='COMPLETED' and len(d['events'])>=5
        pending=admin.call('/bookings/'+str(state['pending']))
        assert pending['booking']['status'] in {'PENDING','EXPIRED'} and len(pending['events'])>=2
        assert any(b['status']=='ACTIVE' for b in admin.call('/resources/'+str(state['resource'])+'/blocks'))
        print('PASS: restart retained booking status, history, resource and maintenance');return
    assert not STATE.exists(), 'State exists; use fresh disposable database or verify'
    roles={r['name']:r['id'] for r in admin.call('/admin/roles')}
    department=admin.call('/admin/departments','POST',{'name':'预约验收测试部门'})['id']
    pw='Aa9'+secrets.token_urlsafe(24)
    accounts={}
    for name,role,dept in [('meet-test-employee','员工',department),('meet-test-manager','资源管理员',department),('meet-test-outsider','员工',1)]:
        accounts[name]=admin.call('/admin/users','POST',{'username':name,'displayName':{'meet-test-employee':'员工（验收测试）','meet-test-manager':'资源负责人（验收测试）','meet-test-outsider':'其他部门（验收测试）'}[name],'password':pw,'roleId':roles[role],'departmentId':dept,'enabled':True})['id']
    employee=Client(args.base);employee.login('meet-test-employee',pw)
    manager=Client(args.base);manager.login('meet-test-manager',pw)
    outsider=Client(args.base);outsider.login('meet-test-outsider',pw)
    now=dt.datetime.fromisoformat(employee.call('/options')['serverNow'].replace('Z','+00:00'))
    start=dt.datetime.fromtimestamp((int(now.timestamp())//900+1)*900,dt.timezone.utc);end=start+dt.timedelta(hours=1)
    assert start.astimezone(dt.timezone(dt.timedelta(hours=8))).hour<22, 'Run before Shanghai 22:00 for same-day workflow'
    def resource(code,name,review=True,shared=True,buffer=0):
        return manager.call('/resources','POST',{'code':code,'name':name,'location':'隔离测试环境','category':'ROOM','departmentId':department,'stewardId':accounts['meet-test-manager'],'capacity':12,'openMinute':0,'closeMinute':1440,'bufferMinutes':buffer,'maxDurationMinutes':240,'minNoticeMinutes':0,'checkInGraceMinutes':15,'weekdays':'1,2,3,4,5,6,7','approvalRequired':review,'shared':shared,'enabled':True})
    room=resource('TEST-ROOM','会议室（验收测试）')
    pending_room=resource('TEST-REVIEW','审批会议室（验收测试）',buffer=15)
    race_room=resource('TEST-RACE','并发预约会议室（验收测试）',review=False,buffer=15)
    private_room=resource('TEST-PRIVATE','部门私有会议室（验收测试）',shared=False)
    def draft(who,r,from_time=start,to=end,title='项目评审（验收测试）'):
        return who.call('/bookings','POST',{'resourceId':r['id'],'title':title,'purpose':'仅用于隔离测试库的预约流程验收','attendees':4,'startsAt':stamp(from_time),'endsAt':stamp(to),'requestKey':key()})
    def act(who,b,action,expected=200,request_key=None): return who.call(f'/bookings/{b["id"]}/commands/{action}','POST',{'version':b['version'],'requestKey':request_key or key(),'note':'预约流程验收测试'},expected)
    b=draft(employee,room); b=act(employee,b,'submit'); idem=key(); b=act(manager,b,'approve',request_key=idem)
    replay=act(manager,{**b,'version':b['version']-1},'approve',request_key=idem); assert replay['id']==b['id']
    conflict=draft(outsider,room);act(outsider,conflict,'submit',409)
    outsider.call('/bookings/'+str(b['id']),expected=403);outsider.call('/admin/users',expected=403)
    cal=outsider.call('/calendar?day='+start.astimezone(dt.timezone(dt.timedelta(hours=8))).date().isoformat())
    slots=[s for r in cal['resources'] for s in r['slots']]
    assert any(s.get('label')=='BUSY' and 'bookingId' not in s and 'title' not in s for s in slots)
    assert all(r['resource']['id']!=private_room['id'] for r in cal['resources'])
    b=act(employee,b,'check-in'); stale={**b,'version':b['version']-1};act(employee,stale,'finish',409)
    b=act(employee,b,'finish'); assert b['status']=='COMPLETED'
    detail=employee.call('/bookings/'+str(b['id'])); assert len(detail['events'])>=5
    report=employee.call('/bookings/'+str(b['id'])+'/report.json'); raw=json.dumps(report);assert 'password' not in raw.lower() and 'zhuatech' not in raw.lower()
    # Independent concurrent sessions submit against the same real MySQL resource.
    a=draft(employee,race_room);c=draft(outsider,race_room);gate=threading.Barrier(2)
    def compete(client,booking):
        gate.wait()
        try: act(client,booking,'submit');return 200
        except AssertionError as error:
            assert 'got 409' in str(error),str(error);return 409
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        futures=[pool.submit(compete,employee,a),pool.submit(compete,outsider,c)];assert sorted(f.result() for f in futures)==[200,409]
    # Maintenance cannot silently override a submitted booking; cancellation retains history.
    manager.call('/resources/'+str(race_room['id'])+'/blocks','POST',{'startsAt':stamp(start),'endsAt':stamp(end),'reason':'并发资源维护验收'},409)
    pending=draft(employee,pending_room,title='待审批预约（验收测试）');pending=act(employee,pending,'submit')
    block=manager.call('/resources/'+str(room['id'])+'/blocks','POST',{'startsAt':stamp(end+dt.timedelta(hours=1)),'endsAt':stamp(end+dt.timedelta(hours=2)),'reason':'设备维护（验收测试）'})
    blocked=draft(employee,room,end+dt.timedelta(hours=1),end+dt.timedelta(hours=2));act(employee,blocked,'submit',409)
    changed=manager.call('/resources/'+str(room['id'])+'/blocks/'+str(block['id'])+'/cancel','POST',{'version':block['version']});assert changed['status']=='CANCELLED'
    block=manager.call('/resources/'+str(room['id'])+'/blocks','POST',{'startsAt':stamp(end+dt.timedelta(hours=2)),'endsAt':stamp(end+dt.timedelta(hours=3)),'reason':'后续维护（验收测试）'})
    state={'password':pw,'accounts':accounts,'department':department,'resource':room['id'],'finished':b['id'],'pending':pending['id'],'start':stamp(start),'end':stamp(end)}
    fd=os.open(STATE,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as out: json.dump(state,out)
    print('PASS: real MySQL workflow, idempotency, stale versions, concurrent single winner, privacy, permissions, maintenance and export')
if __name__=='__main__': main()
