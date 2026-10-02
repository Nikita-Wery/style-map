const AppUtils = {
    getCsrfToken: () => document.querySelector("meta[name='_csrf']")?.getAttribute("content"),
    getCsrfHeader: () => document.querySelector("meta[name='_csrf_header']")?.getAttribute("content"),
};

function toggleProfileDropdown() {
    const dropdown = document.getElementById('profileDropdown');
    if (dropdown) dropdown.classList.toggle('show');
}

document.addEventListener('click', function(event) {
    const navRight = document.querySelector('.nav-right');
    const dropdown = document.getElementById('profileDropdown');
    if (navRight && !navRight.contains(event.target) && dropdown && dropdown.classList.contains('show')) {
        dropdown.classList.remove('show');
    }
});

function toggleUserFollow(authorId, btnElement, onSuccessCallback) {
    const originalText = btnElement.innerText;
    btnElement.innerText = '⏳...';
    btnElement.disabled = true;

    fetch(`/api/actions/users/${authorId}/toggle-follow`, {
        method: 'POST',
        headers: { [AppUtils.getCsrfHeader()]: AppUtils.getCsrfToken() }
    })
        .then(res => {
            if (!res.ok) throw new Error("Network response was not ok");
            return res.json();
        })
        .then(data => {
            if(data.status === 'success') {
                if(data.action === 'followed') {
                    btnElement.innerText = 'Отписаться';
                    btnElement.classList.add('btn-secondary');
                } else {
                    btnElement.innerText = 'Подписаться';
                    btnElement.classList.remove('btn-secondary');
                }
                if (onSuccessCallback) onSuccessCallback(data.action);
            } else {
                alert("Ошибка сервера: " + data.message);
                btnElement.innerText = originalText;
            }
        })
        .catch(err => {
            console.error("Ошибка при подписке:", err);
            btnElement.innerText = originalText;
        })
        .finally(() => {
            btnElement.disabled = false;
        });
}