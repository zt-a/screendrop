(function () {
  'use strict';

  var qs = new URLSearchParams(location.search);
  var token = qs.get('token') || '';
  var state = { session: null, files: [], uploading: false, closed: false };

  function $(id) { return document.getElementById(id); }

  var ERROR_TEXTS = {
    FILE_TOO_LARGE: 'Файл превышает лимит размера',
    UNSUPPORTED_TYPE: 'Этот тип файла не поддерживается',
    TOO_MANY_UPLOADS: 'Слишком много одновременных загрузок — попробуйте ещё раз',
    EMPTY_FILE: 'Файл пустой',
    NO_FILE: 'Файл не получен',
    MULTIPLE_FILES: 'Отправляйте по одному файлу за раз'
  };

  var EXT_RE = /\.(pdf|ppt|pptx|doc|docx|txt|jpg|jpeg|png|mp4)$/i;

  function setStatus(text, ok) {
    var el = $('statusLine');
    el.textContent = text;
    el.style.color = ok ? '#66d9a5' : '#ff6b6b';
  }

  function fmtSize(n) {
    if (n >= 1048576) return (n / 1048576).toFixed(1) + ' МБ';
    if (n >= 1024) return (n / 1024).toFixed(1) + ' КБ';
    return n + ' Б';
  }

  function checkSession() {
    if (state.closed || state.uploading) return;
    fetch('/api/session', { headers: { Authorization: 'Bearer ' + token } })
      .then(function (r) {
        if (r.status === 401 || r.status === 403) { showClosed(); return null; }
        if (!r.ok) throw new Error('status ' + r.status);
        return r.json();
      })
      .then(function (s) {
        if (!s) return;
        state.session = s;
        setStatus('Соединено с экраном · приём файлов открыт', true);
      })
      .catch(function () {
        setStatus('Нет связи с экраном — проверьте Wi-Fi', false);
      });
  }

  function showClosed() {
    state.closed = true;
    $('mainCard').classList.add('hidden');
    $('closedCard').classList.remove('hidden');
    setStatus('Сессия закрыта', false);
  }

  function updateUploadBtn() {
    $('uploadBtn').disabled = state.uploading || state.files.length === 0 || state.closed;
  }

  function progressRow(name) {
    var row = document.createElement('div');
    row.className = 'prow';
    row.innerHTML =
      '<div class="top"><span class="pname"></span><span class="pct">0%</span></div>' +
      '<div class="bar"><div class="fill"></div></div>' +
      '<div class="msg"></div>';
    row.querySelector('.pname').textContent = name;
    $('progressArea').appendChild(row);
    return {
      set: function (p) {
        row.querySelector('.pct').textContent = Math.round(p * 100) + '%';
        row.querySelector('.fill').style.width = (p * 100) + '%';
      },
      done: function (msg) {
        row.classList.add('done');
        row.querySelector('.msg').textContent = msg || 'Отправлено';
      },
      fail: function (msg) {
        row.classList.add('fail');
        row.querySelector('.msg').textContent = msg || 'Ошибка';
      }
    };
  }

  function renderFiles() {
    var ul = $('fileList');
    ul.innerHTML = '';
    state.files.forEach(function (f) {
      var li = document.createElement('li');
      li.className = 'file';
      var info = document.createElement('div');
      info.className = 'info';
      var nm = document.createElement('div');
      nm.className = 'name';
      nm.textContent = f.name;
      var sz = document.createElement('div');
      sz.className = 'size';
      sz.textContent = fmtSize(f.size);
      info.appendChild(nm);
      info.appendChild(sz);
      var rm = document.createElement('button');
      rm.className = 'rm';
      rm.textContent = '×';
      rm.title = 'Убрать из списка';
      rm.onclick = function () {
        var i = state.files.indexOf(f);
        if (i >= 0) state.files.splice(i, 1);
        renderFiles();
      };
      li.appendChild(info);
      li.appendChild(rm);
      ul.appendChild(li);
    });
    updateUploadBtn();
  }

  function addFiles(list) {
    var max = (state.session && state.session.maxFileSizeBytes) || 100 * 1024 * 1024;
    Array.prototype.forEach.call(list, function (f) {
      if (!EXT_RE.test(f.name)) {
        var r1 = progressRow(f.name);
        r1.set(1); r1.fail('Неподдерживаемый формат');
        return;
      }
      if (f.size > max) {
        var r2 = progressRow(f.name);
        r2.set(1); r2.fail('Превышает лимит (' + fmtSize(max) + ')');
        return;
      }
      if (f.size === 0) {
        var r3 = progressRow(f.name);
        r3.set(1); r3.fail('Файл пустой');
        return;
      }
      state.files.push(f);
    });
    renderFiles();
  }

  function uploadFile(file) {
    var row = progressRow(file.name);
    return new Promise(function (resolve) {
      var fd = new FormData();
      fd.append('senderName', ($('sender').value || '').trim().slice(0, 64));
      fd.append('file', file, file.name);
      var xhr = new XMLHttpRequest();
      xhr.open('POST', '/api/upload');
      xhr.setRequestHeader('Authorization', 'Bearer ' + token);
      xhr.upload.onprogress = function (e) {
        if (e.lengthComputable) row.set(e.loaded / e.total);
      };
      xhr.onload = function () {
        if (xhr.status === 201) {
          var sha = '';
          try { sha = JSON.parse(xhr.responseText).file.sha256.slice(0, 8); } catch (e) {}
          row.done('Получено · SHA-256: ' + sha);
          resolve(true);
        } else if (xhr.status === 401 || xhr.status === 403) {
          row.fail('Сессия закрыта');
          showClosed();
          resolve(false);
        } else {
          var msg = 'Ошибка ' + xhr.status;
          try {
            var body = JSON.parse(xhr.responseText);
            msg = (body.error && (ERROR_TEXTS[body.error.code] || body.error.message)) || msg;
          } catch (e) {}
          row.fail(msg);
          resolve(false);
        }
      };
      xhr.onerror = function () { row.fail('Связь с экраном прервана'); resolve(false); };
      xhr.send(fd);
    });
  }

  function uploadAll() {
    if (state.uploading || state.closed) return;
    state.uploading = true;
    updateUploadBtn();
    var remaining = [];
    var seq = state.files.slice();
    function next(i) {
      if (i >= seq.length || state.closed) {
        state.files = state.closed ? seq.slice(i).concat(remaining) : remaining;
        state.uploading = false;
        renderFiles();
        return;
      }
      uploadFile(seq[i]).then(function (ok) {
        if (!ok && !state.closed) remaining.push(seq[i]);
        next(i + 1);
      });
    }
    next(0);
  }

  $('fileInput').addEventListener('change', function (e) {
    addFiles(e.target.files);
    e.target.value = '';
  });
  $('uploadBtn').addEventListener('click', uploadAll);

  checkSession();
  setInterval(checkSession, 5000);
})();
