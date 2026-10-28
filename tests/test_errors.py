from test_auth import client


def test_unknown_page_uses_styled_error_page(client):
    page = client.get('/definitely-missing')
    assert page.status_code == 404
    assert page.headers['content-type'].startswith('text/html')
    assert 'data-code="404"' in page.text
    assert 'assets/js/error.js' in page.text


def test_unknown_api_stays_json(client):
    response = client.get('/api/v6/nope')
    assert response.status_code == 404
    assert response.headers['content-type'].startswith('application/json')
    assert response.json()['error']['code'] == 'not_found'


def test_error_page_is_served_directly(client):
    response = client.get('/error.html')
    assert response.status_code == 200
    assert 'data-code="{{CODE}}"' in response.text
