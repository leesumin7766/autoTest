// Only observed menu paths supplied by the server may be retried. Ancestors are transit only.
const prefix = (a,b) => Array.isArray(a)&&Array.isArray(b)&&a.length<=b.length&&a.every((part,i)=>part===b[i]);
function retryPlan(selection) {
  if (!selection) return null;
  if (!['SELF','SUBTREE'].includes(selection.scope)||!Array.isArray(selection.targets)||!selection.targets.length||selection.targets.some(t=>!t.menuKey||!Array.isArray(t.path)||!t.path.length))throw new Error('CRAWL_SCOPE_INVALID');
  const targets=selection.targets;
  const captures=node=>!!node&&targets.some(t=>node.menuKey===t.menuKey||(selection.scope==='SUBTREE'&&node.path.length>t.path.length&&prefix(t.path,node.path)));
  return { targets, captures, visits:node=>captures(node)||targets.some(t=>prefix(node.path,t.path)), covers:item=>targets.some(t=>item.menuKey===t.menuKey||(selection.scope==='SUBTREE'&&item.path?.length>t.path.length&&prefix(t.path,item.path))) };
}
module.exports={retryPlan};
