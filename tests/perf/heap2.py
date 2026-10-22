import json,sys,collections
snap=json.load(open(sys.argv[1]));TARGETS=sys.argv[2:]
meta=snap['snapshot']['meta'];nf=meta['node_fields'];ef=meta['edge_fields'];nt=meta['node_types'][0];et=meta['edge_types'][0]
N=snap['nodes'];E=snap['edges'];S=snap['strings'];NFL=len(nf);EFL=len(ef)
iname=nf.index('name');itype=nf.index('type');iec=nf.index('edge_count')
ietype=ef.index('type');iename=ef.index('name_or_index');ito=ef.index('to_node')
n=len(N)//NFL;first=[0]*(n+1);acc=0
for i in range(n):first[i]=acc;acc+=N[i*NFL+iec]
first[n]=acc
nm=lambda i:S[N[i*NFL+iname]];ty=lambda i:nt[N[i*NFL+itype]]
def en(t,x):return S[x] if et[t] in ('context','property','internal','shortcut') else str(x)
par=[-1]*n;pe=[None]*n;par[0]=0;q=collections.deque([0])
while q:
  i=q.popleft()
  for e in range(first[i],first[i+1]):
    b=e*EFL;t=E[b+ietype]
    if et[t] in ('weak',):continue
    to=E[b+ito]//NFL
    if par[to]==-1:par[to]=i;pe[to]=(t,E[b+iename]);q.append(to)
for T in TARGETS:
  ids=[i for i in range(n) if nm(i).startswith(T)]
  print(f'== {T}: {len(ids)}')
  paths=collections.Counter();ex={}
  for i in ids[:300]:
    chain=[];cur=i
    while cur!=0 and par[cur]!=-1 and len(chain)<40:
      t,x=pe[cur];chain.append(f"{ty(par[cur])}:{nm(par[cur])[:50]} .{en(t,x)[:40]}");cur=par[cur]
    key=' <- '.join(c for c in chain if not c.startswith('native:<'))[:400]
    paths[key]+=1;ex[key]=chain
  for k,c in paths.most_common(4):
    print(c);[print('   ',x) for x in ex[k][:22]]
