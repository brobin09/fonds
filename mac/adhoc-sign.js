// Ad-hoc sign the final .app so Apple Silicon Macs will launch it (no Developer ID needed).
const { execFileSync } = require('child_process');
const path = require('path');
exports.default = async function (context) {
  if (process.platform !== 'darwin') return;
  const app = path.join(context.appOutDir, `${context.packager.appInfo.productFilename}.app`);
  execFileSync('codesign', ['--force', '--deep', '--sign', '-', app], { stdio: 'inherit' });
  execFileSync('codesign', ['--verify', '--deep', '--strict', '--verbose=2', app], { stdio: 'inherit' });
};
